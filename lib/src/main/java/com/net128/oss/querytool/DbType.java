package com.net128.oss.querytool;

public enum DbType {
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