package com.net128.oss.querytool;

import java.time.Duration;

public record Query(String sql, DbType dbType, Duration minTTL, Duration maxTTL) {}
