package fptu.semester8.mss301.propertiesservice.config;

import fptu.semester8.mss301.propertiesservice.features.room.api.InternalRoomController;
import fptu.semester8.mss301.propertiesservice.features.room.domain.Room;
import fptu.semester8.mss301.propertiesservice.features.room.infrastructure.persistence.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalRoomController.class)
@Import({PropertyApiSecurityConfig.class, PropertyInternalAuthFilter.class})
class PropertyInternalApiTest {
    private static final String KEY = "test-only-property-internal-key-32-bytes-minimum";
    @Autowired MockMvc mvc;
    @MockitoBean RoomRepository rooms;

    @Test
    void internalRoomRequiresServiceCredential() throws Exception {
        mvc.perform(get("/internal/rooms/12")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/rooms/12").header(PropertyInternalAuthFilter.HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(rooms);
    }

    @Test
    void validAssetRequestGetsExactConsumerFields() throws Exception {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(12L);
        when(room.getBuildingId()).thenReturn(34L);
        when(rooms.findById(12L)).thenReturn(Optional.of(room));
        mvc.perform(get("/internal/rooms/12").header(PropertyInternalAuthFilter.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.buildingId").value(34));
    }

    @Test
    void unknownInternalPathStaysClosedWithValidCredential() throws Exception {
        mvc.perform(get("/internal/unknown").header(PropertyInternalAuthFilter.HEADER, KEY))
                .andExpect(status().isUnauthorized());
    }
}
