package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.QueryBuilder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * The MOHECORE column catalogue.
 *
 * <p>Built here rather than read from {@code V_Query_Builder}, which hardcoded
 * three of its six columns: {@code column_description} was the literal
 * {@code N''} on all 1,351 rows even though 318 columns carry an MS_Description
 * property, and {@code is_filterable} / {@code is_sortable} were the constant 1,
 * so "filterable columns" meant every column in the schema. It also excluded
 * every {@code V_*} object, hiding all 39 reporting views — the objects most
 * worth querying — while including credential columns and superseded backups.
 *
 * <p>V024 brings the view into line; querying the catalogue here means the page
 * does not have to wait for it.
 */
@Repository
public interface QueryBuilderRepository extends JpaRepository<QueryBuilder, QueryBuilder.QueryBuilderId> {

    /**
     * Types SQL Server will not filter or sort on without an explicit cast, so
     * offering them as filterable would build a query that fails at run time.
     */
    String UNORDERABLE_TYPES =
            "('text','ntext','image','xml','varbinary','binary','geography','geometry','hierarchyid','sql_variant')";

    String ORDERABLE_FLAG =
            "CASE WHEN TYPE_NAME(c.user_type_id) IN " + UNORDERABLE_TYPES + " THEN 0 ELSE 1 END";

    String CATALOG = """
            SELECT
                o.name AS table_name,
                c.name AS column_name,
                TYPE_NAME(c.user_type_id) AS data_type,
                CAST(ISNULL(CAST(ep.value AS NVARCHAR(400)), N'') AS NVARCHAR(400)) AS column_description,
                """ + ORDERABLE_FLAG + """
                 AS is_filterable,
                """ + ORDERABLE_FLAG + """
                 AS is_sortable
            FROM sys.objects o
            INNER JOIN sys.columns c ON c.object_id = o.object_id
            LEFT JOIN sys.extended_properties ep
                   ON ep.major_id = c.object_id
                  AND ep.minor_id = c.column_id
                  AND ep.class = 1
                  AND ep.name = 'MS_Description'
            WHERE o.schema_id = SCHEMA_ID('MOHECORE')
              AND o.type IN ('U', 'V')
              AND o.is_ms_shipped = 0
              -- Superseded copies, not part of the model.
              AND o.name NOT LIKE '%[_][_][_]old'
              AND o.name NOT LIKE '%[_]BKUP'
              AND o.name NOT LIKE '%[_]BAK'
              AND o.name <> 'sysdiagrams'
              -- Credentials have no place in a catalogue, and this catalogue is
              -- what will decide which columns a query may select.
              AND c.name NOT LIKE '%PASSWORD%'
              AND c.name NOT LIKE '%TOKEN%'
              AND c.name NOT LIKE '%SECRET%'
              AND c.name NOT LIKE '%[_]SALT%'
              AND c.name NOT LIKE '%APIKEY%'
              AND c.name NOT LIKE '%API[_]KEY%'
              -- Gateway webhook headers (SMS, WhatsApp) carry the bearer token.
              AND c.name NOT LIKE '%WEBHOOK[_]HEADERS%'
            """;

    @Query(value = CATALOG + " ORDER BY o.name, c.column_id", nativeQuery = true)
    List<QueryBuilder> findCatalog();

    @Query(value = CATALOG + " AND o.name = :tableName ORDER BY c.column_id", nativeQuery = true)
    List<QueryBuilder> findCatalogForTable(@Param("tableName") String tableName);

    @Query(value = CATALOG + " AND " + ORDERABLE_FLAG + " = 1 ORDER BY o.name, c.column_id",
            nativeQuery = true)
    List<QueryBuilder> findFilterableColumns();

    @Query(value = "SELECT DISTINCT cat.table_name FROM (" + CATALOG + ") cat ORDER BY cat.table_name",
            nativeQuery = true)
    List<String> findAllTableNames();

    /** The same object filter the catalogue uses, for joining against it. */
    String CATALOGUED_OBJECT = """
            o.schema_id = SCHEMA_ID('MOHECORE')
              AND o.type IN ('U', 'V')
              AND o.is_ms_shipped = 0
              AND o.name NOT LIKE '%[_][_][_]old'
              AND o.name NOT LIKE '%[_]BKUP'
              AND o.name NOT LIKE '%[_]BAK'
              AND o.name <> 'sysdiagrams'
            """;

    /**
     * Ways the given object can be joined to another.
     *
     * <p>Two sources, unioned. The declared foreign keys are authoritative but
     * cover only tables — the reporting views have none, and they are what a
     * report is usually built from. So columns that share a key-shaped name
     * across two objects are offered as well, marked {@code NAME} so the caller
     * can tell a fact from a guess.
     *
     * <p>A bare {@code Id} is excluded from the name matching: most tables have
     * one, and matching them to each other would suggest joining every object in
     * the schema to every other.
     */
    String JOIN_SUGGESTIONS = """
            SELECT po.name AS fromTable, pc.name AS fromColumn,
                   ro.name AS toTable,  rc.name AS toColumn, 'FK' AS kind
            FROM sys.foreign_key_columns fkc
            INNER JOIN sys.objects po ON po.object_id = fkc.parent_object_id
            INNER JOIN sys.objects ro ON ro.object_id = fkc.referenced_object_id
            INNER JOIN sys.columns pc ON pc.object_id = fkc.parent_object_id
                                     AND pc.column_id = fkc.parent_column_id
            INNER JOIN sys.columns rc ON rc.object_id = fkc.referenced_object_id
                                     AND rc.column_id = fkc.referenced_column_id
            WHERE po.schema_id = SCHEMA_ID('MOHECORE')
              AND ro.schema_id = SCHEMA_ID('MOHECORE')
              AND (po.name = :tableName OR ro.name = :tableName)

            UNION

            SELECT o.name AS fromTable, c.name AS fromColumn,
                   o2.name AS toTable, c2.name AS toColumn, 'NAME' AS kind
            FROM sys.objects o
            INNER JOIN sys.columns c ON c.object_id = o.object_id
            INNER JOIN sys.columns c2 ON c2.name = c.name
            INNER JOIN sys.objects o2 ON o2.object_id = c2.object_id
            WHERE o.name = :tableName
              AND o2.name <> o.name
              AND o2.schema_id = SCHEMA_ID('MOHECORE')
              AND o2.type IN ('U', 'V')
              AND o2.is_ms_shipped = 0
              AND o2.name NOT LIKE '%[_][_][_]old'
              AND o2.name NOT LIKE '%[_]BKUP'
              AND o2.name NOT LIKE '%[_]BAK'
              AND o2.name <> 'sysdiagrams'
              AND c.name NOT IN ('ID', 'Id', 'id')
              AND (c.name LIKE '%ID' OR c.name LIKE 'STUDY[_]%[_]IN')
            """;

    @Query(value = JOIN_SUGGESTIONS + " ORDER BY kind, toTable, toColumn", nativeQuery = true)
    List<com.dilaw.mohe.dto.JoinSuggestionDto> findJoinSuggestions(@Param("tableName") String tableName);
}
