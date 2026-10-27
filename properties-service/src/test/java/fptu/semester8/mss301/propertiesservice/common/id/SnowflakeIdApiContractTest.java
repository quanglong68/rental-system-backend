package fptu.semester8.mss301.propertiesservice.common.id;

import fptu.semester8.mss301.propertiesservice.features.room.api.dto.RoomRequest;
import fptu.semester8.mss301.propertiesservice.features.room.api.dto.RoomResponse;
import fptu.semester8.mss301.propertiesservice.features.room.domain.Room;
import fptu.semester8.mss301.propertiesservice.features.room.mapper.RoomMapperImpl;
import fptu.semester8.mss301.propertiesservice.features.building.api.dto.BuildingResponse;
import fptu.semester8.mss301.propertiesservice.features.building.api.dto.BuildingSearchResponse;
import fptu.semester8.mss301.propertiesservice.features.province.api.dto.ProvinceResponse;
import fptu.semester8.mss301.propertiesservice.features.roomprice.api.dto.RoomPriceResponse;
import fptu.semester8.mss301.propertiesservice.features.roomtype.api.dto.RoomTypeResponse;
import fptu.semester8.mss301.propertiesservice.features.ward.api.dto.WardResponse;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SnowflakeIdApiContractTest {
    private static final String LARGE_ID = "898785309339156480";

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void mapsAndSerializesLargeIdsAsStringsForSingleAndCollectionResponses() {
        Room room = new Room(Long.parseLong(LARGE_ID), Long.parseLong(LARGE_ID) + 1,
                Long.parseLong(LARGE_ID) + 2, "A-101", 4, "AVAILABLE",
                new BigDecimal("35.5"), 1, 1, new BigDecimal("5000000"), null);
        RoomResponse response = new RoomMapperImpl().toResponse(room);
        String singleJson = json.writeValueAsString(response);
        String collectionJson = json.writeValueAsString(List.of(response));

        JsonNode single = json.readTree(singleJson);
        JsonNode collectionItem = json.readTree(collectionJson).get(0);
        assertEquals(LARGE_ID, single.path("id").asString());
        assertTrue(single.path("id").isTextual());
        assertEquals(Long.toString(Long.parseLong(LARGE_ID) + 1), single.path("buildingId").asString());
        assertTrue(single.path("buildingId").isTextual());
        assertTrue(single.path("roomTypeId").isTextual());
        assertEquals(LARGE_ID, collectionItem.path("id").asString());
        var page = new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1);
        JsonNode pageItem = json.readTree(json.writeValueAsString(page)).path("content").get(0);
        assertEquals(LARGE_ID, pageItem.path("id").asString());
        assertTrue(single.path("maxCapacity").isIntegralNumber());
        assertTrue(single.path("basePrice").isNumber());
        assertEquals("5000000", single.path("basePrice").asString());
    }

    @Test
    void serializesIdsInEveryResourceResponseAsStrings() {
        long id = Long.parseLong(LARGE_ID);
        List<Object> responses = List.of(
                new BuildingResponse(id, "Green", "12 Main", id + 1, id + 2,
                        null, null, null, null, null, null),
                new BuildingSearchResponse(id, "Green", "12 Main", id + 2, "Province",
                        id + 1, "Ward", null, null, null, null, null, null, null, null),
                new ProvinceResponse(id, "01", "Province", null, null, null),
                new WardResponse(id, id + 1, "00001", "Ward", null, null, null),
                new RoomTypeResponse(id, "STUDIO", "Studio", null, null, null),
                new RoomPriceResponse(id, id + 1, new BigDecimal("5000000"), null, null, null, null)
        );
        JsonNode array = json.readTree(json.writeValueAsString(responses));

        assertId(array.get(0), "buildingId", LARGE_ID);
        assertId(array.get(0), "wardId", Long.toString(id + 1));
        assertId(array.get(0), "provinceId", Long.toString(id + 2));
        assertId(array.get(1), "buildingId", LARGE_ID);
        assertId(array.get(1), "provinceId", Long.toString(id + 2));
        assertId(array.get(1), "wardId", Long.toString(id + 1));
        assertId(array.get(2), "provinceId", LARGE_ID);
        assertId(array.get(3), "wardId", LARGE_ID);
        assertId(array.get(3), "provinceId", Long.toString(id + 1));
        assertId(array.get(4), "id", LARGE_ID);
        assertId(array.get(5), "id", LARGE_ID);
        assertId(array.get(5), "roomId", Long.toString(id + 1));
        assertTrue(array.get(5).path("price").isNumber());
    }

    private static void assertId(JsonNode json, String field, String expected) {
        assertTrue(json.path(field).isTextual(), field + " should be a JSON string");
        assertEquals(expected, json.path(field).asString());
    }

    @Test
    void parsesQuotedLargeRequestIdsWithoutLosingPrecision() {
        RoomRequest request = json.readValue("""
                {"buildingId":"898785309339156480","roomTypeId":"898785309339156481",
                 "roomCode":"A-101","maxCapacity":4,"area":35.5,
                 "bedroomCount":1,"bathroomCount":1,"basePrice":5000000,"status":"AVAILABLE"}
                """, RoomRequest.class);

        assertEquals(898785309339156480L, request.buildingId());
        assertEquals(898785309339156481L, request.roomTypeId());
        assertEquals(new BigDecimal("5000000"), request.basePrice());
    }

    @Test
    void rejectsOutOfRangeAndNonPositiveRequestIds() {
        assertThrows(RuntimeException.class, () -> json.readValue("""
                {"buildingId":"9223372036854775808","roomTypeId":"1"}
                """, RoomRequest.class));
        assertThrows(RuntimeException.class, () -> json.readValue("""
                {"buildingId":"not-a-number","roomTypeId":"1"}
                """, RoomRequest.class));

        RoomRequest request = new RoomRequest(-1L, 1L, "A-101", 4,
                new BigDecimal("35.5"), 1, 1, new BigDecimal("5000000"), "AVAILABLE");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(request);
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("buildingId")));
        }
    }
}
