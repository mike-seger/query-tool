package com.net128.oss.querytool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
public class QueryService {
    private final JdbcTemplate jdbcTemplate;
    private final QueryToolConfiguration queryToolConfiguration;
    private final DataSource dataSource;
    private final DbType dbType;
    private final String currentSchema;
    private final LinkedHashMap<String, Query> predefinedQueries;
    private final LinkedHashMap<String, String> queries;
    private final AsyncCacheManager<String> asyncCacheManager;

    public QueryService(JdbcTemplate jdbcTemplate,
            QueryToolConfiguration queryToolConfiguration, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryToolConfiguration = queryToolConfiguration;
        this.dataSource = dataSource;
        this.dbType = DbType.fromString(getDatabaseType().toLowerCase());
        this.currentSchema = getCurrentSchema();
        this.asyncCacheManager = new AsyncCacheManager<>();
        this.predefinedQueries = getPredefinedQueries();
        this.queries = getQueries();
    }

    public String executeQuery(String sql) throws SQLException {
        if(!queryToolConfiguration.isCustomQueries())
            throw new SQLException(
                "The current QueryToolConfiguration doesn't allow arbitrary queries");
        return executeQueryPrivate(sql);
    }

    public String executeQueryByKey(String key) throws SQLException {
        var sql = getQueries().get(key);
        if (sql == null) throw new IllegalArgumentException("Invalid key: " + key);
        try {
            return asyncCacheManager.readEntry(key).get();
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    public LinkedHashMap<String, String> getQueries() {
        if(queries!=null) return queries;
        LinkedHashMap<String, String> queries = getPredefinedQueries().entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> entry.getValue().sql(),
            (oldValue, newValue) -> oldValue,
            LinkedHashMap::new
        ));
        if(queryToolConfiguration.isCustomQueries())
            getTables(true).stream().sorted().forEach(name -> queries.put(
                name.toLowerCase(), String.format("select * from %s limit 100", name)));
        return queries;
    }

    private String escapeChars(String s) {
        if(s==null) return s;
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
        try (Connection connection = dataSource.getConnection()) {
            try (ResultSet rs = connection.createStatement().executeQuery("SELECT current_schema()")) {
                if (rs.next()) return rs.getString(1);
            }
        } catch(Exception e) {
            log.error("Unable to get current schema", e);
        }
        return null;
    }

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

    private LinkedHashMap<String, Query> getPredefinedQueries() {
        if(predefinedQueries!=null) return predefinedQueries;
        var queries = new LinkedHashMap<>(queryToolConfiguration.getQueries());
        var queryNames = new LinkedHashSet<>(queries.keySet());
        var filteredQueries = new LinkedHashMap<String, Query>();
        queryNames.forEach(
            name -> {
                if(name.matches("^[a-z0-9]+__.*")) {
                    if(isDbSpecificQuery(name))  {
                        var newName=name.replaceAll(".*__", "○ ").replace("_", " ");
                        filteredQueries.put(newName, queries.get(name));
                    }
                } else filteredQueries.put(name, queries.get(name));
            }
        );

        for (Map.Entry<String, Query> entry : filteredQueries.entrySet()) {
            String key = entry.getKey();
            Query query = entry.getValue();
            asyncCacheManager.addEntry(key,
                query.maxTTL()==null?Duration.ofMinutes(60):query.maxTTL(),
                query.minTTL()==null?Duration.ofSeconds(10):query.minTTL(),
                q -> CompletableFuture.supplyAsync(() -> {
                    try { return executeQueryPrivate(query.sql());
                    } catch (SQLException e) { throw new RuntimeException(e); }
                }));
        }
        return filteredQueries;
    }

    private boolean isDbSpecificQuery(String name) {
        return (name.startsWith("h2__")&&dbType==DbType.h2) ||
            (name.startsWith("postgres__")&&dbType==DbType.postgres) ||
            (name.startsWith("oracle__")&&dbType==DbType.oracle) ||
            (name.startsWith("mysql__")&&dbType==DbType.mysql);
    }
}
