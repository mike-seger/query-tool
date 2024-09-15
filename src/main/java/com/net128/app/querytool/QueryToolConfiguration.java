package com.net128.app.querytool;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "com.net128.app.querytool")
@Data
public class QueryToolConfiguration {
    private Map<String, String> queries = new LinkedHashMap<>();
    private boolean customQueries = false;
}
