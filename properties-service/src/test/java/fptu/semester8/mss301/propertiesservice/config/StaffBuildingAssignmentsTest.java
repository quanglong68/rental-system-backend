package fptu.semester8.mss301.propertiesservice.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StaffBuildingAssignmentsTest {
    private static final String KEY = "test-only-staff-assignment-key-32-bytes-minimum";
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test
    void readsAssignedBuildingsWithInternalCredential() throws Exception {
        start(200, "{\"staffId\":51,\"buildingIds\":[10,11]}");
        assertEquals(Set.of(10L, 11L), client(KEY).forStaff(51));
    }

    @Test
    void wrongStaffAndDownstreamFailureGrantNothing() throws Exception {
        start(200, "{\"staffId\":52,\"buildingIds\":[10]}");
        assertTrue(client(KEY).forStaff(51).isEmpty());
        assertTrue(client("missing").forStaff(51).isEmpty());
    }

    private void start(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/staff/51/buildings", exchange -> {
            boolean trusted = KEY.equals(exchange.getRequestHeaders().getFirst("X-Staff-Assignment-Key"));
            int responseStatus = trusted ? status : 401;
            byte[] bytes = (trusted ? body : "").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
    }

    private StaffBuildingAssignments client(String key) {
        return new StaffBuildingAssignments("http://127.0.0.1:" + server.getAddress().getPort(), key);
    }
}
