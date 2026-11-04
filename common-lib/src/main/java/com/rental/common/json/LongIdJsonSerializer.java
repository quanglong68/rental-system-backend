package com.rental.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/**
 * Writes ids above JavaScript's safe-integer limit (2^53-1) as JSON strings so the browser cannot
 * round them. Smaller values keep their numeric form, so existing clients and tests that read a
 * count or an identity id as a number see no change.
 *
 * <p>Snowflake ids (property service) are ~8.9e17 and lose their low bits when parsed by
 * {@code JSON.parse}; those are exactly the values this serializer escapes as text. Deserialization
 * needs no counterpart: Jackson already accepts a quoted string for a {@code Long} field.
 */
public final class LongIdJsonSerializer extends JsonSerializer<Long> {

    /** Biggest integer JavaScript represents exactly. */
    public static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    @Override
    public void serialize(Long value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        if (value == null) {
            generator.writeNull();
        } else if (value > MAX_SAFE_INTEGER || value < -MAX_SAFE_INTEGER) {
            generator.writeString(value.toString());
        } else {
            generator.writeNumber(value.longValue());
        }
    }
}
