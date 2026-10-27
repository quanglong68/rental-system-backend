package fptu.semester8.mss301.propertiesservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads staff assignments from their owning service; failures grant no buildings. */
@Component
public class StaffBuildingAssignments {
    private final RestClient client;
    private final String key;

    public StaffBuildingAssignments(
            @Value("${property.security.user-service-url:http://localhost:8082}") String userServiceUrl,
            @Value("${property.security.staff-assignment-key:}") String key) {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(2000);
        requests.setReadTimeout(2000);
        this.client = RestClient.builder().baseUrl(userServiceUrl).requestFactory(requests).build();
        this.key = key;
    }

    public Set<Long> forStaff(long staffId) {
        if (staffId <= 0 || key.length() < 32) return Set.of();
        try {
            @SuppressWarnings("unchecked") Map<String, Object> body = client.get()
                    .uri("/internal/staff/{staffId}/buildings", staffId)
                    .header("X-Staff-Assignment-Key", key)
                    .retrieve().body(Map.class);
            if (body == null || !(body.get("staffId") instanceof Number id)
                    || id.longValue() != staffId || !(body.get("buildingIds") instanceof List<?> raw)) {
                return Set.of();
            }
            Set<Long> ids = new HashSet<>();
            for (Object value : raw) {
                if (!(value instanceof Number number) || number.longValue() <= 0) return Set.of();
                ids.add(number.longValue());
            }
            return Set.copyOf(ids);
        } catch (RuntimeException unavailable) {
            return Set.of();
        }
    }
}
