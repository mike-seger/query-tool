package com.net128.app.querytool;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "com.net128.app.querytool")
public class QueryToolConfiguration {

    private Map<String, String> queries = new LinkedHashMap<>();

    public Map<String, String> getQueries() {
        return queries;
    }

    public void setQueries(Map<String, String> queries) {
        this.queries = queries;
    }
}
