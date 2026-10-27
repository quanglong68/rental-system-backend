package com.rental.listingservice.common.id;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdGenerator implements IdGenerator {
    private final de.mkammerer.snowflakeid.SnowflakeIdGenerator delegate;

    public SnowflakeIdGenerator(@Value("${snowflake.generator-id:0}") int generatorId) {
        this.delegate = de.mkammerer.snowflakeid.SnowflakeIdGenerator.createDefault(generatorId);
    }

    @Override
    public long nextId() {
        return delegate.next();
    }
}
