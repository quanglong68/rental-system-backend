package fptu.semester8.mss301.propertiesservice.config;

import fptu.semester8.mss301.propertiesservice.features.building.api.BuildingController;
import fptu.semester8.mss301.propertiesservice.features.building.application.BuildingService;
import fptu.semester8.mss301.propertiesservice.features.building.mapper.BuildingMapper;
import fptu.semester8.mss301.propertiesservice.features.roomtype.api.RoomTypeController;
import fptu.semester8.mss301.propertiesservice.features.roomtype.application.RoomTypeService;
import fptu.semester8.mss301.propertiesservice.features.roomtype.mapper.RoomTypeMapper;
import fptu.semester8.mss301.propertiesservice.features.imagestorage.api.ImageStorageController;
import fptu.semester8.mss301.propertiesservice.features.imagestorage.application.ImageStorageService;
import fptu.semester8.mss301.propertiesservice.features.imagestorage.application.PropertyImageService;
import fptu.semester8.mss301.propertiesservice.features.room.api.RoomController;
import fptu.semester8.mss301.propertiesservice.features.room.application.RoomService;
import fptu.semester8.mss301.propertiesservice.features.room.mapper.RoomMapper;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {BuildingController.class, RoomTypeController.class, ImageStorageController.class, RoomController.class})
@Import({PropertyApiSecurityConfig.class, PropertyGatewayTrustFilter.class})
class PropertyApiSecurityTest {
    private static final String KEY = "test-only-property-gateway-key-32-bytes-minimum";

    @Autowired MockMvc mvc;
    @MockitoBean BuildingService buildings;
    @MockitoBean BuildingMapper buildingMapper;
    @MockitoBean RoomTypeService roomTypes;
    @MockitoBean RoomTypeMapper roomTypeMapper;
    @MockitoBean PropertyScope scope;
    @MockitoBean ImageStorageService imageStorageService;
    @MockitoBean PropertyImageService propertyImages;
    @MockitoBean RoomService rooms;
    @MockitoBean RoomMapper roomMapper;

    @Test
    void protectedListRequiresIdentityAndAdminRole() throws Exception {
        mvc.perform(get("/api/v1/buildings").header(PropertyGatewayTrustFilter.HEADER, KEY))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/buildings").header(PropertyGatewayTrustFilter.HEADER, KEY)
                        .header("X-User-Id", "2").header("X-User-Type", "CUSTOMER")
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden());
        when(buildings.list(isNull(), any())).thenReturn(Page.empty());
        when(scope.isAdmin()).thenReturn(true);
        mvc.perform(get("/api/v1/buildings").header(PropertyGatewayTrustFilter.HEADER, KEY)
                        .header("X-User-Id", "1").header("X-User-Type", "ADMIN")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void publicReadsNeedGatewayButNoLogin() throws Exception {
        when(roomTypes.list(any())).thenReturn(Page.empty());
        mvc.perform(get("/api/v1/room-types").header(PropertyGatewayTrustFilter.HEADER, KEY))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/room-types"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/buildings/12").header(PropertyGatewayTrustFilter.HEADER, KEY))
                .andExpect(status().isOk());
        when(buildings.nearby(any(), any(), anyDouble(), any())).thenReturn(Page.empty());
        mvc.perform(get("/api/v1/buildings/nearby?latitude=10&longitude=106&radiusMeters=5000")
                        .header(PropertyGatewayTrustFilter.HEADER, KEY))
                .andExpect(status().isOk());
    }

    @Test
    void unknownWritesAndInternalPathsAreClosed() throws Exception {
        mvc.perform(post("/api/v1/buildings").header(PropertyGatewayTrustFilter.HEADER, KEY))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/rooms/1").header(PropertyGatewayTrustFilter.HEADER, KEY)
                        .header("X-User-Id", "1").header("X-User-Type", "ADMIN")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void imageMutationChecksOwnerScopeAndGenericUploadIsAdminOnly() throws Exception {
        doThrow(new AccessDeniedException("outside assignment")).when(scope).requireBuilding(11);
        mvc.perform(patch("/api/v1/images/buildings/11/2")
                        .header(PropertyGatewayTrustFilter.HEADER, KEY)
                        .header("X-User-Id", "51").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY")
                        .contentType("application/json")
                        .content("{\"displayOrder\":0,\"primaryImage\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/images/buildings/10/2")
                        .header(PropertyGatewayTrustFilter.HEADER, KEY)
                        .header("X-User-Id", "51").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY")
                        .contentType("application/json")
                        .content("{\"displayOrder\":0,\"primaryImage\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/images/properties")
                        .header(PropertyGatewayTrustFilter.HEADER, KEY)
                        .header("X-User-Id", "51").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCannotMoveRoomIntoUnassignedBuilding() throws Exception {
        String room = "{\"buildingId\":11,\"roomTypeId\":2,\"roomCode\":\"A-1\",\"maxCapacity\":2,"
                + "\"area\":25,\"bedroomCount\":1,\"bathroomCount\":1,\"basePrice\":100,\"status\":\"AVAILABLE\"}";
        doThrow(new AccessDeniedException("outside assignment")).when(scope).requireBuilding(11);
        mvc.perform(put("/api/v1/rooms/8").header(PropertyGatewayTrustFilter.HEADER, KEY)
                        .header("X-User-Id", "51").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY")
                        .contentType("application/json").content(room))
                .andExpect(status().isForbidden());
        verify(scope).requireRoom(8);
        verify(scope).requireBuilding(11);
        verify(rooms, never()).update(anyLong(), any());
    }
}
