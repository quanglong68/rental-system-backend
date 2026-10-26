package com.rental.asset.service;

import com.rental.asset.client.PropertyClient;
import com.rental.asset.dto.ApplianceCreateRequest;
import com.rental.asset.dto.ApplianceUpdateRequest;
import com.rental.asset.dto.PageResponse;
import com.rental.asset.dto.UploadUrlRequest;
import com.rental.asset.dto.UploadUrlResponse;
import com.rental.asset.dto.WarrantyUpdateRequest;
import com.rental.asset.entity.Appliance;
import com.rental.asset.repository.ApplianceRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApplianceService {

    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final ApplianceRepository applianceRepository;
    private final PropertyClient propertyClient;
    private final AssetAccessService assetAccessService;
    private final MinioClient minioClient;
    private final PageRequestFactory pageRequestFactory;
    private final String imageBucket;

    public ApplianceService(
            ApplianceRepository applianceRepository,
            PropertyClient propertyClient,
            AssetAccessService assetAccessService,
            MinioClient minioClient,
            PageRequestFactory pageRequestFactory,
            @Value("${asset.minio.bucket}") String imageBucket) {
        this.applianceRepository = applianceRepository;
        this.propertyClient = propertyClient;
        this.assetAccessService = assetAccessService;
        this.minioClient = minioClient;
        this.pageRequestFactory = pageRequestFactory;
        this.imageBucket = imageBucket;
    }

    public Appliance createAppliance(ApplianceCreateRequest request) {
        PropertyClient.RoomInfo roomInfo = propertyClient.getRoom(request.roomId());

        Appliance appliance = new Appliance();
        appliance.setRoomId(request.roomId());
        appliance.setBuildingId(roomInfo.buildingId());
        appliance.setName(request.name());
        appliance.setStatus(request.status() == null ? "GOOD" : request.status());
        return applianceRepository.save(appliance);
    }

    public PageResponse<Appliance> findAppliances(
            Long roomId, Long buildingId, String status, int pageNumber, int pageSize) {
        PageRequest pageRequest = pageRequestFactory.create(pageNumber, pageSize);

        if (roomId != null) {
            PropertyClient.RoomInfo roomInfo = propertyClient.getRoom(roomId);
            assetAccessService.requireBuildingAccess(roomInfo.buildingId());
            return PageResponse.from(applianceRepository.findByRoomId(roomId, pageRequest));
        }

        if (buildingId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cần roomId hoặc buildingId");
        }

        assetAccessService.requireBuildingAccess(buildingId);
        if (status == null) {
            return PageResponse.from(applianceRepository.findByBuildingId(buildingId, pageRequest));
        }
        return PageResponse.from(applianceRepository.findByBuildingIdAndStatus(buildingId, status, pageRequest));
    }

    public Appliance getAppliance(Long applianceId) {
        Appliance appliance = findRequired(applianceRepository.findById(applianceId));
        assetAccessService.requireBuildingAccess(appliance.getBuildingId());
        return appliance;
    }

    public Appliance updateAppliance(Long applianceId, ApplianceUpdateRequest request) {
        Appliance appliance = findRequired(applianceRepository.findById(applianceId));
        assetAccessService.requireBuildingAccess(appliance.getBuildingId());
        appliance.setName(request.name());
        if (request.status() != null) {
            appliance.setStatus(request.status());
        }
        return applianceRepository.save(appliance);
    }

    public Appliance updateWarranty(Long applianceId, WarrantyUpdateRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày hết hạn phải sau ngày bắt đầu");
        }

        Appliance appliance = findRequired(applianceRepository.findById(applianceId));
        assetAccessService.requireBuildingAccess(appliance.getBuildingId());
        appliance.setWarrantyProvider(request.provider());
        appliance.setWarrantyStartDate(request.startDate());
        appliance.setWarrantyEndDate(request.endDate());
        return applianceRepository.save(appliance);
    }

    public void deleteAppliance(Long applianceId) {
        Appliance appliance = findRequired(applianceRepository.findById(applianceId));
        assetAccessService.requireBuildingAccess(appliance.getBuildingId());
        applianceRepository.delete(appliance);
    }

    public UploadUrlResponse createReportImageUploadUrl(UploadUrlRequest request) {
        String contentType = request.contentType().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_IMAGE_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ hỗ trợ JPG, PNG hoặc WEBP");
        }

        String fileExtension = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> "webp";
        };
        String objectName = UUID.randomUUID() + "." + fileExtension;

        try {
            String uploadUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(imageBucket)
                            .object(objectName)
                            .expiry(10, TimeUnit.MINUTES)
                            .extraHeaders(Map.of("Content-Type", contentType))
                            .build());
            String imageUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(imageBucket)
                            .object(objectName)
                            .expiry(7, TimeUnit.DAYS)
                            .build());
            return new UploadUrlResponse(uploadUrl, imageUrl, 600);
        } catch (Exception exception) {
            throw new com.rental.common.error.BusinessException(
                    "DEPENDENCY_UNAVAILABLE", "Không thể tạo liên kết tải ảnh", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private Appliance findRequired(Optional<Appliance> applianceOptional) {
        return applianceOptional.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
