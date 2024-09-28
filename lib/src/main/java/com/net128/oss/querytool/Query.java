package com.net128.oss.querytool;

import java.time.Duration;

public record Query(
    String sql,
    Duration minTTL,
    Duration maxTTL) {}
