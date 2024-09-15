package com.net128.app.querytool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Service
@Slf4j
public class QueryService {
    private final JdbcTemplate jdbcTemplate;
    private final QueryToolConfiguration queryToolConfiguration;
    private final DataSource dataSource;
    private final DbType dbType;
    private final String currentSchema;

    public QueryService(JdbcTemplate jdbcTemplate,
                        QueryToolConfiguration queryToolConfiguration, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryToolConfiguration = queryToolConfiguration;
        this.dataSource = dataSource;
        this.dbType = DbType.fromString(getDatabaseType().toLowerCase());
        this.currentSchema = getCurrentSchema();
    }

    public String executeQuery(String sql) throws SQLException {
        var columnNames = new ArrayList<String>();
        var rows = new ArrayList<String>();

        try {
            var rowSet = jdbcTemplate.queryForRowSet(sql);
            for (var i = 1; i <= rowSet.getMetaData().getColumnCount(); i++) {
                columnNames.add(rowSet.getMetaData().getColumnName(i));
            }

            while (rowSet.next()) {
                var row = new ArrayList<String>();
                for (var columnName : columnNames) {
                    row.add(rowSet.getString(columnName));
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

    public String getDatabaseType() {
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

    public List<String> getTables(boolean onlyCurrentSchema) {
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

    public String executeQueryByKey(String key) throws SQLException {
        var sql = queryToolConfiguration.getQueries().get(key);
        if (sql == null) throw new IllegalArgumentException("Invalid key: " + key);
        return executeQuery(sql);
    }

    public Map<String, String> getQueries() {
        var queries = new LinkedHashMap<>(queryToolConfiguration.getQueries());
        getTables(true).stream().sorted().forEach(name -> queries.put(
            name.toLowerCase(), String.format("select * from %s limit 100", name)));
        return getFilteredQueries(queries);
    }

    private boolean isDbSpecificQuery(String name) {
        return (name.startsWith("h2__")&&dbType==DbType.h2) ||
            (name.startsWith("postgres__")&&dbType==DbType.postgres) ||
            (name.startsWith("oracle__")&&dbType==DbType.oracle) ||
            (name.startsWith("mysql__")&&dbType==DbType.mysql);
    }

    private LinkedHashMap<String, String> getFilteredQueries(LinkedHashMap<String, String> queries) {
        var queryNames = new LinkedHashSet<>(queries.keySet());
        var filteredQueries = new LinkedHashMap<String, String>();
        queryNames.forEach(
            name -> {
                if(name.matches("^[a-z0-9]+__.*")) {
                    if(isDbSpecificQuery(name))  {
                        var newName=name.replaceAll(".*__", "").replace("_", " ");
                        filteredQueries.put(newName, queries.get(name));
                    }
                } else filteredQueries.put(name, queries.get(name));
            }
        );
        return filteredQueries;
    }

    private enum DbType {
        h2, postgres, mysql, oracle, unsupported;
        public static DbType fromString(String dbTypeString) {
            var s = dbTypeString.toLowerCase();
            if(s.contains("postgres")) return postgres;
            else if(s.contains("h2")) return h2;
            else if(s.contains("oracle")) return oracle;
            else if(s.contains("mysql")) return mysql;
            else if(s.contains("maria")) return mysql;
            return unsupported;
        }
    }
}
