package com.net128.oss.querytool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class QueryService {
    private final JdbcTemplate jdbcTemplate;
    private final QueryToolConfiguration queryToolConfiguration;
    private final DataSource dataSource;
    private final DbType dbType;
    private final String currentSchema;
    private final LinkedHashMap<String, Query> predefinedQueries;
    private final AsyncCacheManager<String> asyncCacheManager;

    public QueryService(JdbcTemplate jdbcTemplate,
            QueryToolConfiguration queryToolConfiguration, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryToolConfiguration = queryToolConfiguration;
        this.dataSource = dataSource;
        this.dbType = DbType.fromString(getDatabaseType().toLowerCase());
        this.currentSchema = getCurrentSchema();
        this.asyncCacheManager = new AsyncCacheManager<>();

        fixSqlCompatibility(queryToolConfiguration);
        this.predefinedQueries = getPredefinedQueries();
    }

    public String executeQuery(String sql) throws SQLException {
        if(!queryToolConfiguration.isCustomQueries())
            throw new SQLException(
                "The current QueryToolConfiguration doesn't allow arbitrary queries");
        var key = sql.trim();
        if(asyncCacheManager.readEntry(key) != null) return executeQueryByKey(sql);
            //addEntryToCache(key, sql, null, null);
        return executeQueryPrivate(sql);
    }

    public String executeQueryByKey(String key) throws SQLException {
        key = key.trim();
        if(asyncCacheManager.readEntry(key) == null)  throw new IllegalArgumentException("Invalid key: " + key);
        try {
            return asyncCacheManager.readEntry(key).get();
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    public LinkedHashMap<String, Query> getPredefinedQueries() {
        if(predefinedQueries!=null) return predefinedQueries;
        var queries = new LinkedHashMap<>(queryToolConfiguration.getQueries());
        var predefinedQueries = new LinkedHashMap<String, Query>();
        queries.forEach((name, value) -> {
            if (name.matches("^[a-z0-9]+:.*")) {
                if (isDbSpecificQuery(name)) predefinedQueries.put("• "+name, queries.get(name));
            } else predefinedQueries.put("• "+name, queries.get(name));
        });

        if(queryToolConfiguration.isCustomQueries())
            getTables(true).forEach((table) -> {
                var query = new Query("select * from "+table+" limit 100", null, null);
                predefinedQueries.put(table.toLowerCase(), query);
            });
        predefinedQueries.forEach((key, query) ->
            addEntryToCache(key, query.getSql(), query.getMaxTTL(), query.getMinTTL()));

        return predefinedQueries;
    }

    private String escapeChars(String s) {
        if(s==null) return null;
        return s.replace("\t", "\\t").replace("\n", "\\n");
    }

    private String executeQueryPrivate(String sql) throws SQLException {
        var columnNames = new ArrayList<String>();
        var rows = new ArrayList<String>();

        try {
            var rowSet = jdbcTemplate.queryForRowSet(sql);
            for (var i = 1; i <= rowSet.getMetaData().getColumnCount(); i++) {
                columnNames.add(escapeChars(rowSet.getMetaData().getColumnName(i)));
            }

            while (rowSet.next()) {
                var row = new ArrayList<String>();
                for (var columnName : columnNames) {
                    row.add(escapeChars(rowSet.getString(columnName)));
                }
                rows.add(String.join("\t", row));
            }

        } catch (Exception e) {
            log.error("Error executing query {}", sql, e);
            throw new SQLException(e.getMessage());
        }

        var header = String.join("\t", columnNames);
        var result = String.join("\n", rows);
        return header + "\n" + result;
    }

    private String getDatabaseType() {
        try (var connection = dataSource.getConnection()) {
            var metaData = connection.getMetaData();
            return metaData.getDatabaseProductName();
        } catch(Exception e) {
            log.error("Unknown DB type", e);
            return "unknown";
        }
    }

    private String getCurrentSchema() {
            var currentSchemaFunction = this.dbType==DbType.mysql?"DATABASE()":"current_schema()";
            try (var connection = dataSource.getConnection()) {
                try (var rs = connection.createStatement().executeQuery("SELECT "+currentSchemaFunction)) {
                    if (rs.next()) return rs.getString(1);
                }
            } catch(Exception e) {
            log.error("Unable to get current schema", e);
        }
        return null;
    }

    @SuppressWarnings("SameParameterValue")
    private List<String> getTables(boolean onlyCurrentSchema) {
        var tables = new ArrayList<String>();
        try (var connection = dataSource.getConnection()) {
            var metaData = connection.getMetaData();
            var rs = metaData.getTables(null,
                onlyCurrentSchema?currentSchema:null,
                "%", new String[]{"TABLE"});
            while (rs.next()) {
                var tableName = rs.getString("TABLE_NAME");
                tables.add(tableName);
            }
        } catch (Exception e) {
            log.error("Error getting table names from current schema", e);
        }
        return tables;
    }

    private void addEntryToCache(String key, String sql, Duration maxTTL, Duration minTTL) {
        asyncCacheManager.addEntry(key.trim(),
            maxTTL==null?queryToolConfiguration.getMaxTTL():maxTTL,
            minTTL==null?queryToolConfiguration.getMinTTL():minTTL,
            q -> CompletableFuture.supplyAsync(() -> {
                try { return executeQueryPrivate(sql);
                } catch (SQLException e) { throw new RuntimeException(e); }
            }));
    }

    private boolean isDbSpecificQuery(String name) {
        return (name.startsWith("h2:")&&dbType==DbType.h2) ||
            (name.startsWith("postgres:")&&dbType==DbType.postgres) ||
            (name.startsWith("oracle:")&&dbType==DbType.oracle) ||
            (name.startsWith("mysql:")&&dbType==DbType.mysql);
    }

    private void fixSqlCompatibility(QueryToolConfiguration queryToolConfiguration) {
        if(!queryToolConfiguration.isSqlCompatibilityFix()) return;
        queryToolConfiguration.getQueries().values().forEach(query -> {
            if (dbType == DbType.h2) {
                query.setSql(SqlUtils.replaceFunctionCalls(
                    query.getSql(), "md5","rawtohex(hash", "'MD5', ", ")"));
            } if (dbType == DbType.mysql) {
                query.setSql(query.getSql().replace("random()", "rand()"));
            }
        });
    }
}
