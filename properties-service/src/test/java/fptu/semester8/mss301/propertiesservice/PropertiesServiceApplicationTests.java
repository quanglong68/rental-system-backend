package fptu.semester8.mss301.propertiesservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import fptu.semester8.mss301.propertiesservice.support.PostgresIntegrationSupport;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PropertiesServiceApplicationTests extends PostgresIntegrationSupport {

    @Autowired
    MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void invalidPathIdsReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/provinces/0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/provinces/9223372036854775808"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/provinces/not-an-id")).andExpect(status().isBadRequest());
    }

    @Test
    void nonPositiveRequestIdsReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/rooms")
                        .header("X-User-Id", "1")
                        .header("X-User-Type", "ADMIN")
                        .header("X-User-Roles", "ADMIN")
                        .contentType("application/json")
                        .content("""
                                {"buildingId":"-1","roomTypeId":"1","roomCode":"A-101",
                                 "maxCapacity":4,"area":35.5,"bedroomCount":1,
                                 "bathroomCount":1,"basePrice":5000000,"status":"AVAILABLE"}
                                """))
                .andExpect(status().isBadRequest());
    }

}
