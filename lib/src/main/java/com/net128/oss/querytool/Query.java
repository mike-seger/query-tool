package com.net128.oss.querytool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Query {
    private String sql;
    private Duration minTTL;
    private Duration maxTTL;
}
