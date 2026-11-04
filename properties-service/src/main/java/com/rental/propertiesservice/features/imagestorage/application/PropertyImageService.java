package com.rental.propertiesservice.features.imagestorage.application;

import com.rental.common.error.BusinessException;
import com.rental.common.error.NotFoundException;
import com.rental.propertiesservice.common.id.IdGenerator;
import com.rental.propertiesservice.config.BuildingAccessGuard;
import com.rental.propertiesservice.features.building.infrastructure.persistence.repository.SpringDataBuildingRepository;
import com.rental.propertiesservice.features.imagestorage.infrastructure.ObjectStorage;
import com.rental.propertiesservice.features.imagestorage.infrastructure.StorageObject;
import com.rental.propertiesservice.features.room.domain.Room;
import com.rental.propertiesservice.features.room.infrastructure.persistence.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PropertyImageService {
    private final JdbcTemplate jdbc;
    private final IdGenerator ids;
    private final ImageStorageService uploads;
    private final ObjectStorage storage;
    private final SpringDataBuildingRepository buildings;
    private final RoomRepository rooms;
    private final BuildingAccessGuard access;

    public enum Owner {
        BUILDING("building_images", "building_image_id", "building_id", "building"),
        ROOM("room_images", "room_image_id", "room_id", "room");

        final String table, imageColumn, ownerColumn, folder;
        Owner(String table, String imageColumn, String ownerColumn, String folder) {
            this.table = table;
            this.imageColumn = imageColumn;
            this.ownerColumn = ownerColumn;
            this.folder = folder;
        }
    }

    @Transactional
    public ImageRecord upload(Owner owner, long ownerId, MultipartFile file) {
        requireOwner(owner, ownerId);
        assertWriteAccess(owner, ownerId);
        StorageObject uploaded = uploads.upload(owner.folder, ownerId, file);
        try {
            int nextOrder = jdbc.queryForObject("SELECT COALESCE(MAX(display_order), -1) + 1 FROM " + owner.table + " WHERE " + owner.ownerColumn + " = ?", Integer.class, ownerId);
            boolean primary = jdbc.queryForObject("SELECT COUNT(*) = 0 FROM " + owner.table + " WHERE " + owner.ownerColumn + " = ?", Boolean.class, ownerId);
            long imageId = ids.nextId();
            jdbc.update("INSERT INTO " + owner.table + " (" + owner.imageColumn + ", " + owner.ownerColumn + ", image_url, display_order, is_primary_image) VALUES (?, ?, ?, ?, ?)", imageId, ownerId, uploaded.url(), nextOrder, primary);
            return get(owner, ownerId, imageId);
        } catch (RuntimeException ex) {
            try { storage.delete(uploaded.key()); } catch (RuntimeException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<ImageRecord> list(Owner owner, long ownerId) {
        requireOwner(owner, ownerId);
        return jdbc.query("SELECT " + owner.imageColumn + " AS image_id, " + owner.ownerColumn + " AS owner_id, image_url, display_order, is_primary_image, created_at FROM " + owner.table + " WHERE " + owner.ownerColumn + " = ? ORDER BY display_order, " + owner.imageColumn,
                (rs, row) -> new ImageRecord(Long.toString(rs.getLong("image_id")), Long.toString(rs.getLong("owner_id")), rs.getString("image_url"), rs.getInt("display_order"), rs.getBoolean("is_primary_image"), rs.getTimestamp("created_at").toInstant()), ownerId);
    }

    @Transactional(readOnly = true)
    public ImageRecord get(Owner owner, long ownerId, long imageId) {
        return list(owner, ownerId).stream().filter(i -> i.imageId().equals(Long.toString(imageId))).findFirst()
                .orElseThrow(() -> new NotFoundException("IMAGE_NOT_FOUND", "Không tìm thấy ảnh %d".formatted(imageId)));
    }

    @Transactional
    public ImageRecord update(Owner owner, long ownerId, long imageId, Integer displayOrder, Boolean primaryImage) {
        ImageRecord current = get(owner, ownerId, imageId);
        assertWriteAccess(owner, ownerId);
        if (displayOrder != null && displayOrder < 0) {
            throw new BusinessException("INVALID_DISPLAY_ORDER", "Thứ tự hiển thị không được âm",
                    HttpStatus.BAD_REQUEST);
        }
        if (Boolean.TRUE.equals(primaryImage)) {
            jdbc.update("UPDATE " + owner.table + " SET is_primary_image = false WHERE " + owner.ownerColumn + " = ?", ownerId);
        }
        jdbc.update("UPDATE " + owner.table + " SET display_order = ?, is_primary_image = ? WHERE " + owner.imageColumn + " = ? AND " + owner.ownerColumn + " = ?",
                displayOrder == null ? current.displayOrder() : displayOrder,
                primaryImage == null ? current.primaryImage() : primaryImage, imageId, ownerId);
        return get(owner, ownerId, imageId);
    }

    @Transactional
    public void delete(Owner owner, long ownerId, long imageId) {
        ImageRecord current = get(owner, ownerId, imageId);
        assertWriteAccess(owner, ownerId);
        jdbc.update("DELETE FROM " + owner.table + " WHERE " + owner.imageColumn + " = ? AND " + owner.ownerColumn + " = ?", imageId, ownerId);
        if (current.primaryImage()) {
            List<ImageRecord> remaining = list(owner, ownerId);
            if (!remaining.isEmpty()) jdbc.update("UPDATE " + owner.table + " SET is_primary_image = true WHERE " + owner.imageColumn + " = ?", Long.parseLong(remaining.getFirst().imageId()));
        }
    }

    private void requireOwner(Owner owner, long ownerId) {
        boolean exists = owner == Owner.BUILDING ? buildings.findByBuildingIdAndDeletedFalse(ownerId).isPresent() : rooms.existsById(ownerId);
        if (!exists) {
            String label = owner == Owner.BUILDING ? "tòa nhà" : "phòng";
            throw new NotFoundException(owner + "_NOT_FOUND", "Không tìm thấy %s %d".formatted(label, ownerId));
        }
    }

    private void assertWriteAccess(Owner owner, long ownerId) {
        if (owner == Owner.BUILDING) {
            access.assertAssigned(ownerId);
            return;
        }
        Room room = rooms.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng %d".formatted(ownerId)));
        access.assertAssigned(room.getBuildingId());
    }
}
