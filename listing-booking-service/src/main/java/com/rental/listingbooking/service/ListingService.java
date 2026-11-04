package com.rental.listingbooking.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.error.NotFoundException;
import com.rental.listingbooking.dto.ListingResponse;
import com.rental.common.dto.PageResponse;
import com.rental.listingbooking.dto.UpdateListingRequest;
import com.rental.listingbooking.dto.UpdateListingStatusRequest;
import com.rental.listingbooking.entity.RoomListing;
import com.rental.listingbooking.repository.RoomListingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListingService {

    private static final String ACTIVE = "ACTIVE";
    private static final String CLOSED = "CLOSED";

    private final RoomListingRepository listingRepository;
    private final BuildingScope buildingScope;

    @Transactional(readOnly = true)
    public PageResponse<ListingResponse> search(
            Long wardId,
            Long districtId,
            Long buildingId,
            Long roomTypeId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String keyword,
            Double lat,
            Double lng,
            Double radiusKm,
            Pageable pageable) {

        boolean hasLocation = lat != null && lng != null;
        boolean distanceSort = pageable.getSort().stream()
                .anyMatch(order -> "distance".equalsIgnoreCase(order.getProperty()));
        boolean radiusFilter = hasLocation && radiusKm != null && radiusKm > 0;

        if (hasLocation && (distanceSort || radiusFilter)) {
            // Lọc bán kính phải chạy trên toàn bộ tập ứng viên rồi mới phân trang,
            // nếu lọc sau khi phân trang sẽ trả thiếu bản ghi.
            List<ListingResponse> candidates = listingRepository.searchActive(
                            wardId, districtId, buildingId, roomTypeId, minPrice, maxPrice, keyword, Pageable.unpaged())
                    .map(ListingResponse::from)
                    .getContent()
                    .stream()
                    .peek(dto -> {
                        if (dto.getLatitude() != null && dto.getLongitude() != null) {
                            dto.setDistanceKm(haversine(
                                    lat, lng,
                                    dto.getLatitude().doubleValue(),
                                    dto.getLongitude().doubleValue()));
                        }
                    })
                    .filter(dto -> !radiusFilter
                            || (dto.getDistanceKm() != null && dto.getDistanceKm() <= radiusKm))
                    .sorted(comparatorFor(pageable.getSort()))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

            int start = (int) Math.min(pageable.getOffset(), candidates.size());
            int end = Math.min(start + pageable.getPageSize(), candidates.size());
            return PageResponse.of(new ArrayList<>(candidates.subList(start, end)),
                    pageable.getPageNumber(), pageable.getPageSize(), candidates.size());
        }

        Pageable queryPageable = distanceSort
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt"))
                : pageable;
        Page<RoomListing> page = listingRepository.searchActive(
                wardId, districtId, buildingId, roomTypeId, minPrice, maxPrice, keyword, queryPageable);
        return PageResponse.from(page.map(ListingResponse::from));
    }

    @Transactional(readOnly = true)
    public ListingResponse getById(Long id) {
        RoomListing listing = listingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tin đăng ID " + id));
        return ListingResponse.from(listing);
    }

    @Transactional(readOnly = true)
    public ListingResponse getByRoomId(Long roomId) {
        RoomListing listing = listingRepository.findByRoomId(roomId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tin đăng cho phòng ID " + roomId));
        return ListingResponse.from(listing);
    }

    @Transactional(readOnly = true)
    public PageResponse<ListingResponse> manageListings(Long buildingId, String status, Pageable pageable) {
        String normalizedStatus = normalizeStatusFilter(status);
        List<Long> allowedBuildings = buildingScope.allowedBuildingIdsOrNull();

        if (allowedBuildings != null) {
            if (buildingId != null && !allowedBuildings.contains(buildingId)) {
                throw new AccessDeniedException("Nhân viên chưa được phân công tòa nhà này");
            }
            if (buildingId == null) {
                if (allowedBuildings.isEmpty()) {
                    return PageResponse.of(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0);
                }
                return PageResponse.from(listingRepository
                        .findManageIn(allowedBuildings, normalizedStatus, pageable)
                        .map(ListingResponse::from));
            }
        }

        return PageResponse.from(listingRepository
                .findManage(buildingId, normalizedStatus, pageable)
                .map(ListingResponse::from));
    }

    @Transactional
    public ListingResponse updateListing(Long id, UpdateListingRequest req) {
        RoomListing listing = listingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tin đăng ID " + id));
        buildingScope.assertAssigned(listing.getBuildingId());

        if (req.title() != null && !req.title().isBlank()) {
            listing.setTitle(req.title().trim());
        }
        if (req.description() != null) {
            listing.setDescription(req.description().trim());
        }
        if (req.depositAmount() != null) {
            listing.setDepositAmount(req.depositAmount());
        }
        if (req.electricityPrice() != null) {
            listing.setElectricityPrice(req.electricityPrice());
        }
        if (req.waterPrice() != null) {
            listing.setWaterPrice(req.waterPrice());
        }
        if (req.waterUnit() != null) {
            listing.setWaterUnit(req.waterUnit());
        }
        if (req.paymentMethod() != null) {
            listing.setPaymentMethod(req.paymentMethod());
        }
        if (req.paymentDueDay() != null) {
            listing.setPaymentDueDay(req.paymentDueDay());
        }
        if (req.minTermMonths() != null) {
            listing.setMinTermMonths(req.minTermMonths());
        }
        if (req.availableFrom() != null) {
            listing.setAvailableFrom(req.availableFrom());
        }
        listing.setUpdatedAt(Instant.now());
        return ListingResponse.from(listingRepository.save(listing));
    }

    @Transactional
    public ListingResponse updateStatus(Long id, UpdateListingStatusRequest req) {
        RoomListing listing = listingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tin đăng ID " + id));
        buildingScope.assertAssigned(listing.getBuildingId());

        String target = req.status().trim().toUpperCase(Locale.ROOT);
        if (!ACTIVE.equals(target) && !CLOSED.equals(target)) {
            throw new BusinessException("INVALID_STATUS",
                    "Trạng thái hợp lệ chỉ gồm ACTIVE hoặc CLOSED", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        // Spec 9.3: chỉ bật hiển thị khi phòng thực sự còn trống.
        if (ACTIVE.equals(target) && !isRentable(listing.getRoomStatus())) {
            throw new BusinessException("ROOM_NOT_AVAILABLE",
                    "Phòng không còn trống nên không thể bật tin đăng", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        listing.setStatus(target);
        listing.setUpdatedAt(Instant.now());
        return ListingResponse.from(listingRepository.save(listing));
    }

    private static boolean isRentable(String roomStatus) {
        return "VACANT".equals(roomStatus) || "UPCOMING_VACANT".equals(roomStatus);
    }

    private static String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!ACTIVE.equals(normalized) && !CLOSED.equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Bộ lọc trạng thái chỉ gồm ACTIVE hoặc CLOSED");
        }
        return normalized;
    }

    private static Comparator<ListingResponse> comparatorFor(Sort sort) {
        Comparator<ListingResponse> comparator = null;
        for (Sort.Order order : sort) {
            Comparator<ListingResponse> next = switch (order.getProperty()) {
                case "price" -> Comparator.comparing(ListingResponse::getPrice,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "distance" -> Comparator.comparing(ListingResponse::getDistanceKm,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "createdAt" -> Comparator.comparing(ListingResponse::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                default -> null;
            };
            if (next == null) {
                continue;
            }
            if (order.isDescending()) {
                next = next.reversed();
            }
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        if (comparator == null) {
            comparator = Comparator.comparing(ListingResponse::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        return comparator;
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round((R * c) * 100.0) / 100.0;
    }
}
