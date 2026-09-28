<#
.SYNOPSIS
    Restore drill for the encrypted MOHE backups (security plan S-13).

.DESCRIPTION
    Takes the newest backup the application wrote (or the one named), decrypts
    it with the file key(s), restores it into a scratch database on the target
    server, verifies it, compares table row counts and checksums with the live
    database when one is reachable, writes a report, and drops the scratch
    database. A backup that has never been restored is an assumption; this
    turns it into a record.

    For a TDE-protected database (O-09) the backup can only be restored where
    MOHE_TDE_Certificate exists. Pass -CertificateBackup/-CertificateKey/
    -CertificatePassword to install it on the target first; the drill then
    proves the certificate backup is usable, which is the point.

.PARAMETER BackupDir      Where the application writes backups (app.backup.directory).
.PARAMETER BackupFile     A specific .bak (encrypted or not); default: newest in BackupDir.
.PARAMETER Server         Target SQL Server instance (should NOT be the production server).
.PARAMETER User/Password  SQL login on the target with dbcreator (integrated auth when omitted).
.PARAMETER ScratchDb      Name of the scratch database (dropped at the end unless -KeepDatabase).
.PARAMETER DataDir        Where the scratch data/log files go; default: the instance's data path.
.PARAMETER FileKeys       "id:base64[,id:base64]"; default: MOHE_FILE_ENCRYPTION_KEYS / _KEY from the environment.
.PARAMETER SourceServer/SourceDb/SourceUser/SourcePassword  The live database to compare with (optional).
.PARAMETER StagingDir     Where the decrypted copy is written for the restore; the SQL Server service
                          account must be able to read it (default: BackupDir, as the application uses).
.PARAMETER ReportDir      Where the markdown + JSON report goes (default: <BackupDir>\drill-reports).
.PARAMETER Python         Python 3 with the 'cryptography' package (default: python).

.EXAMPLE
    .\dr_drill.ps1 -Server standby,1433 -User sa -Password ... -SourceServer prod,1433 -SourceUser drill_ro -SourcePassword ...
#>
[CmdletBinding()]
param(
    [string]$BackupDir = "C:\MOHE_Backups",
    [string]$BackupFile,
    [Parameter(Mandatory = $true)][string]$Server,
    [string]$User,
    [string]$Password,
    [string]$ScratchDb = "MOHE_DRILL",
    [string]$DataDir,
    [string]$FileKeys,
    [string]$SourceServer,
    [string]$SourceDb = "MOHE",
    [string]$SourceUser,
    [string]$SourcePassword,
    [string]$CertificateBackup,
    [string]$CertificateKey,
    [string]$CertificatePassword,
    [string]$StagingDir,
    [string]$ReportDir,
    [string]$Python = "python",
    [switch]$KeepDatabase
)

$ErrorActionPreference = "Stop"
$started = Get-Date
$stamp = $started.ToString("yyyyMMdd_HHmmss")
if (-not $ReportDir) { $ReportDir = Join-Path $BackupDir "drill-reports" }
if (-not $StagingDir) { $StagingDir = $BackupDir }
New-Item -ItemType Directory -Force $ReportDir | Out-Null
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$mencTool = Join-Path $scriptDir "menc_tool.py"

$report = [ordered]@{
    startedAt = $started.ToString("s"); server = $Server; scratchDb = $ScratchDb
    backup = $null; decryptedWith = $null; header = $null; verifyOnly = $null; restore = $null
    tde = $null; tables = @(); compare = $null; dropped = $false; result = "FAILED"; error = $null
}

function Invoke-Sql {
    param([string]$Instance, [string]$Database, [string]$Query, [string]$U, [string]$P, [switch]$Scalar)
    $args = @("-S", $Instance, "-d", $Database, "-W", "-b", "-h", "-1", "-Q", "SET NOCOUNT ON; $Query")
    if ($U) { $args += @("-U", $U, "-P", $P) } else { $args += "-E" }
    $out = & sqlcmd @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed against $Instance/$Database : $out" }
    return $out
}

try {
    # 1. The backup ------------------------------------------------------------
    if (-not $BackupFile) {
        $newest = Get-ChildItem -Path $BackupDir -Filter "*.bak" -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if (-not $newest) { throw "No .bak file in $BackupDir" }
        $BackupFile = $newest.FullName
    }
    $report.backup = [ordered]@{ path = $BackupFile; bytes = (Get-Item $BackupFile).Length; lastWrite = (Get-Item $BackupFile).LastWriteTime.ToString("s") }
    Write-Host "Backup: $BackupFile"

    # 2. Decrypt (the application encrypts every backup it takes) ---------------
    $staging = Join-Path $StagingDir "drill_$stamp.bak"
    $keyArgs = @()
    if ($FileKeys) { $keyArgs = @("--keys", $FileKeys) }
    $info = & $Python $mencTool info $BackupFile 2>&1
    if ($LASTEXITCODE -ne 0) { throw "menc_tool info failed: $info" }
    if ("$info" -like "MENC*") {
        $dec = & $Python $mencTool decrypt $BackupFile $staging @keyArgs 2>&1
        if ($LASTEXITCODE -ne 0) { throw "Decryption failed: $dec" }
        $report.decryptedWith = "$dec"
        Write-Host "Decrypted: $dec"
    } else {
        Copy-Item $BackupFile $staging -Force
        $report.decryptedWith = "plaintext backup"
    }
    $head = [System.IO.File]::ReadAllBytes($staging)[0..3]
    if (-not ($head[0] -eq 0x54 -and $head[1] -eq 0x41 -and $head[2] -eq 0x50 -and $head[3] -eq 0x45)) {
        throw "The decrypted file does not start with the SQL Server backup signature (TAPE)"
    }

    # 3. Header, TDE, verify ------------------------------------------------------
    $stagingSql = $staging.Replace("'", "''")
    # RESTORE HEADERONLY has no stable column list across versions; read the columns we need by name via a temp table.
    $hdr = Invoke-Sql -Instance $Server -Database master -U $User -P $Password -Query @"
IF OBJECT_ID('tempdb..#h') IS NOT NULL DROP TABLE #h;
CREATE TABLE #h (BackupName nvarchar(128), BackupDescription nvarchar(255), BackupType smallint, ExpirationDate datetime, Compressed bit, Position smallint, DeviceType tinyint, UserName nvarchar(128), ServerName nvarchar(128), DatabaseName nvarchar(128), DatabaseVersion int, DatabaseCreationDate datetime, BackupSize numeric(20,0), FirstLSN numeric(25,0), LastLSN numeric(25,0), CheckpointLSN numeric(25,0), DatabaseBackupLSN numeric(25,0), BackupStartDate datetime, BackupFinishDate datetime, SortOrder smallint, CodePage smallint, UnicodeLocaleId int, UnicodeComparisonStyle int, CompatibilityLevel tinyint, SoftwareVendorId int, SoftwareVersionMajor int, SoftwareVersionMinor int, SoftwareVersionBuild int, MachineName nvarchar(128), Flags int, BindingID uniqueidentifier, RecoveryForkID uniqueidentifier, Collation nvarchar(128), FamilyGUID uniqueidentifier, HasBulkLoggedData bit, IsSnapshot bit, IsReadOnly bit, IsSingleUser bit, HasBackupChecksums bit, IsDamaged bit, BeginsLogChain bit, HasIncompleteMetaData bit, IsForceOffline bit, IsCopyOnly bit, FirstRecoveryForkID uniqueidentifier, ForkPointLSN numeric(25,0), RecoveryModel nvarchar(60), DifferentialBaseLSN numeric(25,0), DifferentialBaseGUID uniqueidentifier, BackupTypeDescription nvarchar(60), BackupSetGUID uniqueidentifier, CompressedBackupSize bigint, Containment tinyint, KeyAlgorithm nvarchar(32), EncryptorThumbprint varbinary(20), EncryptorType nvarchar(32));
INSERT INTO #h EXEC('RESTORE HEADERONLY FROM DISK = ''$stagingSql''');
SELECT DatabaseName + '|' + CONVERT(varchar(30), BackupFinishDate, 126) + '|' + ServerName + '|' + CAST(SoftwareVersionMajor AS varchar(5)) + '|' + CAST(BackupType AS varchar(3)) + '|' + ISNULL(CONVERT(varchar(60), EncryptorThumbprint, 1), '') FROM #h;
"@
    $parts = ("$hdr").Trim().Split("|")
    $report.header = [ordered]@{ databaseName = $parts[0]; backupFinishDate = $parts[1]; serverName = $parts[2]; softwareVersionMajor = $parts[3]; backupType = $parts[4]; encryptorThumbprint = $parts[5] }
    Write-Host ("Header: {0} taken {1} on {2} (version {3})" -f $parts[0], $parts[1], $parts[2], $parts[3])
    if ($parts[4] -ne "1") { throw "Not a full database backup (BackupType=$($parts[4]))" }

    if ($parts[5]) {
        $report.tde = [ordered]@{ encrypted = $true; thumbprint = $parts[5]; certificateInstalled = $false }
        $have = Invoke-Sql -Instance $Server -Database master -U $User -P $Password -Query "SELECT COUNT(*) FROM sys.certificates WHERE name = 'MOHE_TDE_Certificate'"
        if ("$have".Trim() -eq "0") {
            if (-not $CertificateBackup) { throw "The backup is TDE-protected and MOHE_TDE_Certificate is not on $Server; pass -CertificateBackup/-CertificateKey/-CertificatePassword" }
            $cb = $CertificateBackup.Replace("'", "''"); $ck = $CertificateKey.Replace("'", "''"); $cp = $CertificatePassword.Replace("'", "''")
            Invoke-Sql -Instance $Server -Database master -U $User -P $Password -Query @"
IF NOT EXISTS (SELECT 1 FROM sys.symmetric_keys WHERE name = '##MS_DatabaseMasterKey##') CREATE MASTER KEY ENCRYPTION BY PASSWORD = '$cp';
CREATE CERTIFICATE MOHE_TDE_Certificate FROM FILE = '$cb' WITH PRIVATE KEY (FILE = '$ck', DECRYPTION BY PASSWORD = '$cp');
"@ | Out-Null
            Write-Host "Installed MOHE_TDE_Certificate from $CertificateBackup"
        }
        $report.tde.certificateInstalled = $true
    } else {
        $report.tde = [ordered]@{ encrypted = $false }
    }

    Invoke-Sql -Instance $Server -Database master -U $User -P $Password -Query "RESTORE VERIFYONLY FROM DISK = '$stagingSql' WITH CHECKSUM" | Out-Null
    $report.verifyOnly = "OK"
    Write-Host "RESTORE VERIFYONLY: OK"

    # 4. Restore into the scratch database ------------------------------------------
    if (-not $DataDir) {
        $DataDir = ("" + (Invoke-Sql -Instance $Server -Database master -U $User -P $Password -Query "SELECT CAST(SERVERPROPERTY('InstanceDefaultDataPath') AS nvarchar(260))")).Trim()
    }
    $files = Invoke-Sql -Instance $Server -Database master -U $User -P $Password -Query @"
IF OBJECT_ID('tempdb..#f') IS NOT NULL DROP TABLE #f;
CREATE TABLE #f (LogicalName nvarchar(128), PhysicalName nvarchar(260), Type char(1), FileGroupName nvarchar(128), Size numeric(20,0), MaxSize numeric(20,0), FileId bigint, CreateLSN numeric(25,0), DropLSN numeric(25,0), UniqueId uniqueidentifier, ReadOnlyLSN numeric(25,0), ReadWriteLSN numeric(25,0), BackupSizeInBytes bigint, SourceBlockSize int, FileGroupId int, LogGroupGUID uniqueidentifier, DifferentialBaseLSN numeric(25,0), DifferentialBaseGUID uniqueidentifier, IsReadOnly bit, IsPresent bit, TDEThumbprint varbinary(32), SnapshotUrl nvarchar(360));
INSERT INTO #f EXEC('RESTORE FILELISTONLY FROM DISK = ''$stagingSql''');
SELECT LogicalName + '|' + Type FROM #f;
"@
    $moves = @()
    foreach ($line in @($files)) {
        $t = "$line".Trim(); if (-not $t) { continue }
        $lp = $t.Split("|"); $ext = if ($lp[1] -eq "L") { "ldf" } else { "mdf" }
        $target = (Join-Path $DataDir ("{0}_{1}.{2}" -f $ScratchDb, $lp[0], $ext)).Replace("'", "''")
        $moves += "MOVE '$($lp[0].Replace("'", "''"))' TO '$target'"
    }
    $restoreSql = @"
IF DB_ID('$ScratchDb') IS NOT NULL BEGIN ALTER DATABASE [$ScratchDb] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$ScratchDb]; END
RESTORE DATABASE [$ScratchDb] FROM DISK = '$stagingSql' WITH $($moves -join ", "), REPLACE, RECOVERY, STATS = 25;
"@
    $t0 = Get-Date
    Invoke-Sql -Instance $Server -Database master -U $User -P $Password -Query $restoreSql | Out-Null
    $report.restore = [ordered]@{ seconds = [int]((Get-Date) - $t0).TotalSeconds; dataDir = $DataDir }
    Write-Host ("Restored into {0} in {1}s" -f $ScratchDb, $report.restore.seconds)

    # 5. Integrity and content --------------------------------------------------------
    Invoke-Sql -Instance $Server -Database $ScratchDb -U $User -P $Password -Query "DBCC CHECKDB ([$ScratchDb]) WITH NO_INFOMSGS, PHYSICAL_ONLY" | Out-Null
    $report.restore.checkdb = "OK"
    Write-Host "DBCC CHECKDB (PHYSICAL_ONLY): OK"

    # Row counts and a checksum per table, read with the tenant predicate lifted
    # (the row-level security policy hides everything from a session with no scope).
    $countSql = @"
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';
DECLARE @t TABLE (name sysname, n bigint, cs bigint);
DECLARE @s sysname, @n sysname, @sql nvarchar(max);
DECLARE c CURSOR LOCAL FAST_FORWARD FOR
  SELECT s.name, t.name FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id
  WHERE s.name IN ('MOHECORE','UMSCORE','STUDENTCORE','ALERTCORE','FINCORE','AUDITLOG') AND t.is_ms_shipped = 0;
OPEN c; FETCH NEXT FROM c INTO @s, @n;
WHILE @@FETCH_STATUS = 0 BEGIN
  SET @sql = N'SELECT ''' + @s + '.' + @n + ''', COUNT_BIG(*), ISNULL(CHECKSUM_AGG(CAST(BINARY_CHECKSUM(*) AS bigint)), 0) FROM ' + QUOTENAME(@s) + '.' + QUOTENAME(@n) + ' WITH (NOLOCK)';
  BEGIN TRY INSERT INTO @t EXEC sp_executesql @sql; END TRY BEGIN CATCH INSERT INTO @t VALUES (@s + '.' + @n, -1, 0); END CATCH
  FETCH NEXT FROM c INTO @s, @n;
END
CLOSE c; DEALLOCATE c;
SELECT name + '|' + CAST(n AS varchar(20)) + '|' + CAST(cs AS varchar(30)) FROM @t ORDER BY name;
"@
    $scratchRows = @{}
    foreach ($line in @(Invoke-Sql -Instance $Server -Database $ScratchDb -U $User -P $Password -Query $countSql)) {
        $t = "$line".Trim(); if (-not $t) { continue }
        $p = $t.Split("|"); $scratchRows[$p[0]] = @{ rows = [long]$p[1]; checksum = [long]$p[2] }
    }
    $report.tables = @($scratchRows.Keys | Sort-Object | ForEach-Object { [ordered]@{ table = $_; rows = $scratchRows[$_].rows; checksum = $scratchRows[$_].checksum } })
    Write-Host ("{0} tables counted in the scratch database" -f $scratchRows.Count)

    if ($SourceServer) {
        $sourceRows = @{}
        foreach ($line in @(Invoke-Sql -Instance $SourceServer -Database $SourceDb -U $SourceUser -P $SourcePassword -Query $countSql)) {
            $t = "$line".Trim(); if (-not $t) { continue }
            $p = $t.Split("|"); $sourceRows[$p[0]] = @{ rows = [long]$p[1]; checksum = [long]$p[2] }
        }
        $diff = @()
        foreach ($k in ($sourceRows.Keys + $scratchRows.Keys | Sort-Object -Unique)) {
            $a = $sourceRows[$k]; $b = $scratchRows[$k]
            if (-not $a -or -not $b) { $diff += [ordered]@{ table = $k; note = "only in " + $(if ($a) { "source" } else { "backup" }) }; continue }
            if ($a.rows -ne $b.rows -or $a.checksum -ne $b.checksum) {
                $diff += [ordered]@{ table = $k; sourceRows = $a.rows; backupRows = $b.rows; checksumMatch = ($a.checksum -eq $b.checksum) }
            }
        }
        $report.compare = [ordered]@{ source = "$SourceServer/$SourceDb"; tablesCompared = $sourceRows.Count; differences = $diff }
        Write-Host ("Compared with {0}: {1} tables, {2} differ (rows written since the backup are expected here)" -f $SourceServer, $sourceRows.Count, $diff.Count)
    }

    $report.result = "PASSED"
}
catch {
    $report.error = "$_"
    Write-Host "DRILL FAILED: $_" -ForegroundColor Red
}
finally {
    if (-not $KeepDatabase) {
        try {
            Invoke-Sql -Instance $Server -Database master -U $User -P $Password -Query "IF DB_ID('$ScratchDb') IS NOT NULL BEGIN ALTER DATABASE [$ScratchDb] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$ScratchDb]; END" | Out-Null
            $report.dropped = $true
        } catch { Write-Host "Could not drop ${ScratchDb}: $_" -ForegroundColor Yellow }
    }
    if (Test-Path $staging) { Remove-Item $staging -Force }
    $report.finishedAt = (Get-Date).ToString("s")
    $json = Join-Path $ReportDir "drill_$stamp.json"
    $md = Join-Path $ReportDir "drill_$stamp.md"
    ($report | ConvertTo-Json -Depth 6) | Out-File -Encoding utf8 $json
    $lines = @("# MOHE restore drill $stamp", "", "Result: **$($report.result)**", "",
        "| Item | Value |", "|---|---|",
        "| Backup | $($report.backup.path) ($($report.backup.bytes) bytes, $($report.backup.lastWrite)) |",
        "| Decryption | $($report.decryptedWith) |",
        "| Backup of | $($report.header.databaseName) taken $($report.header.backupFinishDate) on $($report.header.serverName) |",
        "| TDE | $(if ($report.tde.encrypted) { 'encrypted, certificate installed: ' + $report.tde.certificateInstalled } else { 'not encrypted' }) |",
        "| VERIFYONLY | $($report.verifyOnly) |",
        "| Restore | $(if ($report.restore) { ('' + $report.restore.seconds + ' s, CHECKDB ' + $report.restore.checkdb) } else { '-' }) |",
        "| Tables counted | $($report.tables.Count) |",
        "| Compared with live | $(if ($report.compare) { $report.compare.tablesCompared.ToString() + ' tables, ' + $report.compare.differences.Count + ' differ' } else { 'not compared' }) |",
        "| Scratch database dropped | $($report.dropped) |",
        "| Error | $($report.error) |")
    $lines | Out-File -Encoding utf8 $md
    Write-Host "Report: $md"
    if ($report.result -ne "PASSED") { exit 1 }
}
