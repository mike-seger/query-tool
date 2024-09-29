package com.net128.oss.querytool;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SqlUtilsTest {
    private final static String sql1 = """
        with num_fields as (
          select
            row_number() over () as id,
            round(random() * 1000) as "field1",  -- random integer between 0 and 999
            round(random() * 1000) as "field2",  -- random integer between 0 and 999
            round(random() * 1000) as "field3",  -- random integer between 0 and 999
            round(random() * 1000) as "field4",  -- random integer between 0 and 999
            round(random() * 1000) as "field5"   -- random integer between 0 and 999
          from generate_series(1, 10)
        )
        select
          id, "field1", "field2", "field3", "field4", "field5",
          md5("field1"::text || "field2"::text || random()::text) as "field6",  -- random string based on numeric "fields" and random()
          md5("field2"::text || "field3"::text || random()::text) as "field7",  -- another random string based on different numeric "fields"
          md5("field3"::text || "field4"::text || random()::text) as "field8"   -- yet another random string
        from num_fields
        """;

    @Test
    public void testMd5ToRawToHexHash() throws NoSuchAlgorithmException {
        var result = SqlUtils.replaceFunctionCalls(sql1,
                "md5","hash", "'MD5', ", "");
        var hash = new java.math.BigInteger(1, MessageDigest.getInstance("MD5").digest(result.getBytes())).toString(16);
        System.out.println(result);
        assertEquals("1c448ec99a97eff9ad250026fe13088a", hash);
    }

    @Test
    public void testNoOperation() {
        var input = "select 1 as N";
        var result = SqlUtils.replaceFunctionCalls(input,
            "md5","hash", "'MD5', ", "");
        assertEquals(input, result);
    }
}
