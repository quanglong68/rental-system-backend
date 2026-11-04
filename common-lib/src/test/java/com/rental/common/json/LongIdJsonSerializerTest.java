package com.rental.common.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LongIdJsonSerializerTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new SimpleModule().addSerializer(Long.class, new LongIdJsonSerializer()));

    @Test
    void smallLong_staysNumeric() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", 42L);

        assertThat(mapper.writeValueAsString(body)).isEqualTo("{\"id\":42}");
    }

    @Test
    void snowflakeLong_isWrittenAsString() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", 890123456789012345L);

        assertThat(mapper.writeValueAsString(body)).isEqualTo("{\"id\":\"890123456789012345\"}");
    }

    @Test
    void maxSafeInteger_staysNumeric() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", LongIdJsonSerializer.MAX_SAFE_INTEGER);

        assertThat(mapper.writeValueAsString(body))
                .isEqualTo("{\"id\":" + LongIdJsonSerializer.MAX_SAFE_INTEGER + "}");
    }

    @Test
    void nullLong_isNull() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", null);

        assertThat(mapper.writeValueAsString(body)).isEqualTo("{\"id\":null}");
    }
}
