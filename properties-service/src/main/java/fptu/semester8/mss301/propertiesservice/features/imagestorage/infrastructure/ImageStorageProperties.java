
package fptu.semester8.mss301.propertiesservice.features.imagestorage.infrastructure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "storage")
@Validated
public record ImageStorageProperties(
        @DefaultValue("r2") String provider,
        @DefaultValue("") String publicBaseUrl,
        @Valid @DefaultValue Image image
) {
    public record Image(
            @Positive @DefaultValue("5242880") long maxSize
    ) {
    }

    public long maxSize() {
        return image.maxSize();
    }
}
