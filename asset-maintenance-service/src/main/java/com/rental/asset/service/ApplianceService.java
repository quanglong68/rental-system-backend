package com.rental.asset.service;

import com.rental.asset.client.PropertyClient;
import com.rental.asset.dto.ApplianceCreateRequest;
import com.rental.asset.dto.ApplianceUpdateRequest;
import com.rental.common.dto.PageResponse;
import com.rental.asset.dto.UploadUrlRequest;
import com.rental.asset.dto.UploadUrlResponse;
import com.rental.asset.dto.WarrantyUpdateRequest;
import com.rental.asset.entity.Appliance;
import com.rental.asset.repository.ApplianceRepository;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ApplianceService {

    private static final Logger log = LoggerFactory.getLogger(ApplianceService.class);
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> SUPPORTED_STATUSES = Set.of("GOOD", "NEEDS_REPAIR", "BROKEN");

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
        PropertyClient.RoomInfo roomInfo = requireRoom(request.roomId());
        assetAccessService.requireBuildingAccess(roomInfo.buildingId());

        Appliance appliance = new Appliance();
        appliance.setRoomId(request.roomId());
        appliance.setBuildingId(roomInfo.buildingId());
        appliance.setName(request.name());
        appliance.setStatus(normalizeStatus(request.status()));
        return applianceRepository.save(appliance);
    }

    public PageResponse<Appliance> findAppliances(
            Long roomId, Long buildingId, String status, int pageNumber, int pageSize) {
        PageRequest pageRequest = pageRequestFactory.create(pageNumber, pageSize);

        if (roomId != null) {
            PropertyClient.RoomInfo roomInfo = requireRoom(roomId);
            assetAccessService.requireBuildingAccess(roomInfo.buildingId());
            return PageResponse.from(applianceRepository.findByRoomId(roomId, pageRequest));
        }

        if (buildingId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cần roomId hoặc buildingId");
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
            appliance.setStatus(normalizeStatus(request.status()));
        }
        return applianceRepository.save(appliance);
    }

    public Appliance updateWarranty(Long applianceId, WarrantyUpdateRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngày hết hạn phải sau ngày bắt đầu");
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
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ hỗ trợ JPG, PNG hoặc WEBP");
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
            log.error("Không tạo được presigned URL cho bucket {}", imageBucket, exception);
            throw new com.rental.common.error.BusinessException(
                    "DEPENDENCY_UNAVAILABLE", "Không thể tạo liên kết tải ảnh", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private PropertyClient.RoomInfo requireRoom(Long roomId) {
        PropertyClient.RoomInfo roomInfo = propertyClient.getRoom(roomId);
        if (roomInfo == null || roomInfo.buildingId() == null) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Không lấy được thông tin phòng");
        }
        return roomInfo;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "GOOD";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "status phải là GOOD/NEEDS_REPAIR/BROKEN");
        }
        return normalized;
    }

    private Appliance findRequired(Optional<Appliance> applianceOptional) {
        return applianceOptional.orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy thiết bị"));
    }
}
