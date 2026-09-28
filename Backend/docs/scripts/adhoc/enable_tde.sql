/*
================================================================================
Transparent Data Encryption for MOHE (security plan O-09, decision D-6)
================================================================================
Ad-hoc DBA script - NOT a numbered migration. Run once per server, by hand, as
a sysadmin, after reading it. It holds a password: change it, run it, and do not
commit the value.

What it does
  1. master database: creates the service master key protection (database
     master key) if missing, and a certificate to protect the encryption key.
  2. MOHE: creates the database encryption key (AES_256) protected by that
     certificate, and switches encryption on.
  3. Prints the BACKUP CERTIFICATE statement you must run at once and store
     off the server. Without that backup a MOHE backup cannot be restored
     anywhere else, including the standby the restore drill uses.

Requirements
  - Enterprise edition on SQL Server 2016/2017; Standard from 2019. Check:
      SELECT SERVERPROPERTY('Edition'), SERVERPROPERTY('ProductVersion');
  - The application does not change. Backups become encrypted; the restore
    endpoint must be tested against one on the target server with the
    certificate installed (docs/DEPLOYMENT_SECURITY.md section 6).
  - Encryption runs in the background; the last query shows progress.
================================================================================
*/

SET NOCOUNT ON;
GO

USE master;
GO

-- 1a. Database master key in master (protects the certificate's private key).
IF NOT EXISTS (SELECT 1 FROM sys.symmetric_keys WHERE name = N'##MS_DatabaseMasterKey##')
BEGIN
    PRINT N'Creating the master database master key...';
    CREATE MASTER KEY ENCRYPTION BY PASSWORD = N'CHANGE-ME-master-key-password-9x!';
END
ELSE
    PRINT N'Master key already exists.';
GO

-- 1b. The certificate that protects MOHE''s database encryption key.
IF NOT EXISTS (SELECT 1 FROM sys.certificates WHERE name = N'MOHE_TDE_Certificate')
BEGIN
    PRINT N'Creating MOHE_TDE_Certificate...';
    CREATE CERTIFICATE MOHE_TDE_Certificate
        WITH SUBJECT = N'MOHE database encryption key protector',
             EXPIRY_DATE = '2036-12-31';
END
ELSE
    PRINT N'MOHE_TDE_Certificate already exists.';
GO

-- 2. The database encryption key, and encryption on.
USE MOHE;
GO

IF NOT EXISTS (SELECT 1 FROM sys.dm_database_encryption_keys WHERE database_id = DB_ID())
BEGIN
    PRINT N'Creating the MOHE database encryption key (AES_256)...';
    CREATE DATABASE ENCRYPTION KEY
        WITH ALGORITHM = AES_256
        ENCRYPTION BY SERVER CERTIFICATE MOHE_TDE_Certificate;
END
ELSE
    PRINT N'MOHE already has a database encryption key.';
GO

IF NOT EXISTS (SELECT 1 FROM sys.databases WHERE name = N'MOHE' AND is_encrypted = 1)
BEGIN
    PRINT N'Switching encryption on (runs in the background)...';
    ALTER DATABASE MOHE SET ENCRYPTION ON;
END
ELSE
    PRINT N'MOHE is already encrypted.';
GO

-- 3. Back the certificate up NOW and move both files off this server.
PRINT N'';
PRINT N'Run this next, then move both files to the key store (not the database backup share):';
PRINT N'  USE master;';
PRINT N'  BACKUP CERTIFICATE MOHE_TDE_Certificate';
PRINT N'      TO FILE = ''D:\keys\MOHE_TDE_Certificate.cer''';
PRINT N'      WITH PRIVATE KEY (FILE = ''D:\keys\MOHE_TDE_Certificate.pvk'',';
PRINT N'                        ENCRYPTION BY PASSWORD = ''<a second, different password>'');';
PRINT N'';
PRINT N'On a restore target: CREATE CERTIFICATE MOHE_TDE_Certificate FROM FILE = ... WITH PRIVATE KEY (...) first.';
GO

-- Progress: encryption_state 3 = encrypted, 2 = in progress with percent_complete.
SELECT DB_NAME(database_id) AS [database], encryption_state, percent_complete,
       key_algorithm, key_length, encryptor_type
FROM sys.dm_database_encryption_keys;
GO
