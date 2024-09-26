package com.net128.oss.querytool;

import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.Map;

@RestController
@RequestMapping("lib/api/query-tool")
public class Controller {

    private final QueryService queryService;
    private final QueryToolConfiguration configuration;

    public Controller(QueryService queryService, QueryToolConfiguration configuration) {
        this.queryService = queryService;
        this.configuration = configuration;
    }

    @PostMapping(value="/query", consumes = "text/plain", produces = "text/tab-separated-values")
    public String executeQuery(@RequestBody String sql) throws SQLException {
        return queryService.executeQuery(sql);
    }

    @GetMapping("/queries")
    public Map<String, String> getQueries() {
        return queryService.getQueries();
    }

    @GetMapping("/configuration")
    public QueryToolConfiguration getConfiguration() {
        return configuration;
    }

    @GetMapping(value="/query/{key}", produces = "text/tab-separated-values")
    public String executeQueryByKey(@PathVariable(name = "key") String key) throws SQLException {
        return queryService.executeQueryByKey(key);
    }
}
