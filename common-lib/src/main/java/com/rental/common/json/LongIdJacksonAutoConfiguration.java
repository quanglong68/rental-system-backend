package com.rental.common.json;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link LongIdJsonSerializer} on every service that depends on common-lib, so no service
 * can silently forget it. Auto-registered through {@code META-INF/spring/...AutoConfiguration.imports}.
 *
 * <p>The customizer applies to the Spring MVC ObjectMapper and to any mapper built by
 * {@code Jackson2ObjectMapperBuilder}; outbox/event payloads created from a raw {@code ObjectMapper}
 * bean are still covered because Spring Boot builds that bean from this builder too.
 */
@AutoConfiguration
public class LongIdJacksonAutoConfiguration {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer longIdJacksonCustomizer() {
        return builder -> {
            LongIdJsonSerializer serializer = new LongIdJsonSerializer();
            builder.serializerByType(Long.class, serializer);
            builder.serializerByType(Long.TYPE, serializer);
        };
    }
}
