package com.net128.oss.querytool;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "com.net128.query-tool")
@Data
public class QueryToolConfiguration {
    private Map<String, Query> queries = new LinkedHashMap<>();
    private boolean customQueries = false;
}
