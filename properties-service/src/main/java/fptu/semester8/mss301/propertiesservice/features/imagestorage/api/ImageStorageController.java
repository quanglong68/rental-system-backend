package fptu.semester8.mss301.propertiesservice.features.imagestorage.api;

import fptu.semester8.mss301.propertiesservice.features.imagestorage.application.ImageStorageService;
import fptu.semester8.mss301.propertiesservice.features.imagestorage.application.PropertyImageService;
import fptu.semester8.mss301.propertiesservice.features.imagestorage.application.ImageRecord;
import fptu.semester8.mss301.propertiesservice.config.PropertyScope;
import static fptu.semester8.mss301.propertiesservice.features.imagestorage.application.PropertyImageService.Owner.*;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/v1/images")
@Tag(name = "Ảnh", description = "Tải ảnh lên object storage")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class ImageStorageController {

    ImageStorageService imageStorageService;
    PropertyImageService propertyImages;
    PropertyScope scope;

    public record ImageUpdate(Integer displayOrder, Boolean primaryImage) {}

    @PostMapping(path = "/buildings/{buildingId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải ảnh tòa nhà",
            description = "Tải một ảnh multipart/form-data lên thư mục building và gắn với buildingId.")
    public ResponseEntity<ImageRecord> uploadBuildingImage(
            @Parameter(description = "ID tòa nhà dạng chuỗi",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480"))
            @PathVariable @Positive long buildingId,
            @Parameter(description = "Tệp ảnh gửi dưới dạng multipart part tên file", required = true)
            @RequestPart("file") MultipartFile file) {
        scope.requireBuilding(buildingId);
        var record = propertyImages.upload(BUILDING, buildingId, file);
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/images/buildings/{id}").buildAndExpand(buildingId).toUri();
        return ResponseEntity.created(location).body(record);
    }

    @PostMapping(path = "/rooms/{roomId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải ảnh phòng",
            description = "Tải một ảnh multipart/form-data lên thư mục room và gắn với roomId.")
    public ResponseEntity<ImageRecord> uploadRoomImage(
            @Parameter(description = "ID phòng dạng chuỗi",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480"))
            @PathVariable @Positive long roomId,
            @Parameter(description = "Tệp ảnh gửi dưới dạng multipart part tên file", required = true)
            @RequestPart("file") MultipartFile file) {
        scope.requireRoom(roomId);
        var record = propertyImages.upload(ROOM, roomId, file);
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/images/rooms/{id}").buildAndExpand(roomId).toUri();
        return ResponseEntity.created(location).body(record);
    }

    @GetMapping("/buildings/{buildingId}")
    public List<ImageRecord> buildingImages(@PathVariable long buildingId) {
        return propertyImages.list(BUILDING, buildingId);
    }

    @GetMapping("/rooms/{roomId}")
    public List<ImageRecord> roomImages(@PathVariable long roomId) {
        return propertyImages.list(ROOM, roomId);
    }

    @PatchMapping("/buildings/{buildingId}/{imageId}")
    public ImageRecord updateBuildingImage(
            @PathVariable long buildingId,
            @PathVariable long imageId,
            @RequestBody ImageUpdate request) {
        scope.requireBuilding(buildingId);
        return propertyImages.update(BUILDING, buildingId, imageId, request.displayOrder(), request.primaryImage());
    }

    @PatchMapping("/rooms/{roomId}/{imageId}")
    public ImageRecord updateRoomImage(
            @PathVariable long roomId,
            @PathVariable long imageId,
            @RequestBody ImageUpdate request) {
        scope.requireRoom(roomId);
        return propertyImages.update(ROOM, roomId, imageId, request.displayOrder(), request.primaryImage());
    }

    @DeleteMapping("/buildings/{buildingId}/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBuildingImage(@PathVariable long buildingId, @PathVariable long imageId) {
        scope.requireBuilding(buildingId);
        propertyImages.delete(BUILDING, buildingId, imageId);
    }

    @DeleteMapping("/rooms/{roomId}/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoomImage(@PathVariable long roomId, @PathVariable long imageId) {
        scope.requireRoom(roomId);
        propertyImages.delete(ROOM, roomId, imageId);
    }

    @PostMapping(path = "/{folderName}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải nhiều ảnh",
            description = "Tải một hoặc nhiều tệp trong multipart/form-data; tên part là files.")
    public ResponseEntity<List<String>> uploadImages(
            @Parameter(description = "Tên thư mục lưu trữ ảnh", example = "properties")
            @PathVariable String folderName,
            @Parameter(description = "Danh sách tệp multipart part tên files", required = true)
            @RequestPart("files") MultipartFile[] files) {
        return ResponseEntity.status(HttpStatus.CREATED).body(imageStorageService.upload(folderName, files));
    }
}
