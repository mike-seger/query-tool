package com.net128.app.querytool;

import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.Map;

@RestController
@RequestMapping("/api/query-tool")
public class Controller {

    private final QueryService queryService;

    public Controller(QueryService queryService) {
        this.queryService = queryService;
    }

    @PostMapping(value="/query", consumes = "text/plain", produces = "text/tab-separated-values")
    public String executeQuery(@RequestBody String sql) throws SQLException {
        return queryService.executeQuery(sql);
    }

    @GetMapping("/queries")
    public Map<String, String> getSqlByKey() {
        return queryService.getQueries();
    }

    @GetMapping(value="/query/{key}", produces = "text/tab-separated-values")
    public String executeQueryByKey(@PathVariable String key) throws SQLException {
        return queryService.executeQueryByKey(key);
    }
}
