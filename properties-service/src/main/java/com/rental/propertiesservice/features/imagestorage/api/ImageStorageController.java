package com.rental.propertiesservice.features.imagestorage.api;

import com.rental.propertiesservice.features.imagestorage.application.ImageStorageService;
import com.rental.propertiesservice.features.imagestorage.application.PropertyImageService;
import com.rental.propertiesservice.features.imagestorage.application.ImageRecord;
import static com.rental.propertiesservice.features.imagestorage.application.PropertyImageService.Owner.*;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/images")
@Tag(name = "Ảnh", description = "Tải ảnh lên object storage")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class ImageStorageController {

    ImageStorageService imageStorageService;
    PropertyImageService propertyImages;

    public record ImageUpdate(Integer displayOrder, Boolean primaryImage) {}

    @PostMapping(
            path = "/buildings/{buildingId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tải ảnh tòa nhà", description = "Tải một ảnh multipart/form-data lên thư mục building và gắn với buildingId. Phần file phải có tên file; giới hạn kích thước cấu hình storage.image.max-size, mặc định 5 MiB.")
    public ImageRecord uploadBuildingImage(
            @Parameter(description = "ID tòa nhà sở hữu ảnh dạng chuỗi", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480")) @PathVariable @Positive long buildingId,
            @Parameter(description = "Tệp ảnh gửi dưới dạng multipart part tên file", required = true)
            @RequestPart("file") MultipartFile file
    ) {
        return propertyImages.upload(BUILDING, buildingId, file);
    }

    @PostMapping(
            path = "/rooms/{roomId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','QUAN_LY')")
    @Operation(summary = "Tải ảnh phòng", description = "Tải một ảnh multipart/form-data lên thư mục room và gắn với roomId. Giới hạn kích thước mỗi ảnh được cấu hình qua storage.image.max-size, mặc định 5 MiB.")
    public ImageRecord uploadRoomImage(
            @Parameter(description = "ID phòng sở hữu ảnh dạng chuỗi", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480")) @PathVariable @Positive long roomId,
            @Parameter(description = "Tệp ảnh gửi dưới dạng multipart part tên file", required = true)
            @RequestPart("file") MultipartFile file
    ) {
        return propertyImages.upload(ROOM, roomId, file);
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
    @PreAuthorize("hasRole('ADMIN')")
    public ImageRecord updateBuildingImage(@PathVariable long buildingId, @PathVariable long imageId, @RequestBody ImageUpdate request) {
        return propertyImages.update(BUILDING, buildingId, imageId, request.displayOrder(), request.primaryImage());
    }

    @PatchMapping("/rooms/{roomId}/{imageId}")
    @PreAuthorize("hasAnyRole('ADMIN','QUAN_LY')")
    public ImageRecord updateRoomImage(@PathVariable long roomId, @PathVariable long imageId, @RequestBody ImageUpdate request) {
        return propertyImages.update(ROOM, roomId, imageId, request.displayOrder(), request.primaryImage());
    }

    @DeleteMapping("/buildings/{buildingId}/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteBuildingImage(@PathVariable long buildingId, @PathVariable long imageId) {
        propertyImages.delete(BUILDING, buildingId, imageId);
    }

    @DeleteMapping("/rooms/{roomId}/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','QUAN_LY')")
    public void deleteRoomImage(@PathVariable long roomId, @PathVariable long imageId) {
        propertyImages.delete(ROOM, roomId, imageId);
    }

    @PostMapping(path = "/{folderName}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tải nhiều ảnh", description = "Tải một hoặc nhiều tệp trong multipart/form-data; tên part là files. folderName xác định thư mục lưu trữ. Mỗi ảnh chịu giới hạn storage.image.max-size, mặc định 5 MiB; tổng request tối đa 50 MiB theo cấu hình Spring.")
    public List<String> uploadImages(
            @Parameter(description = "Tên thư mục lưu trữ ảnh", example = "properties") @PathVariable String folderName,
            @Parameter(description = "Danh sách tệp multipart part tên files", required = true)
            @RequestPart("files") MultipartFile[] files
    ) {
        return imageStorageService.upload(folderName, files);
    }
}
