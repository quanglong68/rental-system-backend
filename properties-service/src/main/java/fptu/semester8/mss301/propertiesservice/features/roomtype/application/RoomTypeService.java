package fptu.semester8.mss301.propertiesservice.features.roomtype.application;

import fptu.exe202.signify.apiresponse.exception.ConflictException;
import fptu.exe202.signify.apiresponse.exception.ResourceNotFoundException;
import fptu.semester8.mss301.propertiesservice.common.id.IdGenerator;
import fptu.semester8.mss301.propertiesservice.features.roomtype.api.dto.RoomTypeRequest;
import fptu.semester8.mss301.propertiesservice.features.roomtype.domain.RoomType;
import fptu.semester8.mss301.propertiesservice.features.roomtype.infrastructure.RoomTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RoomTypeService {

    private final RoomTypeRepository repository;
    private final IdGenerator idGenerator;

    @Transactional
    public RoomType create(RoomTypeRequest request) {
        String typeCode = request.typeCode().trim();
        String name = request.name().trim();
        ensureTypeCodeAvailable(typeCode);
        ensureNameAvailable(name);
        return repository.save(new RoomType(idGenerator.nextId(), typeCode, name,
                request.description(), Instant.now()));
    }

    @Transactional(readOnly = true)
    public RoomType get(long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Room type not found", "ROOM_TYPE_NOT_FOUND"));
    }

    @Transactional(readOnly = true)
    public Page<RoomType> list(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional
    public RoomType update(long id, RoomTypeRequest request) {
        RoomType roomType = get(id);
        String typeCode = request.typeCode().trim();
        String name = request.name().trim();
        if (repository.existsByTypeCodeIgnoreCaseAndIdNot(typeCode, id)) {
            throw typeCodeConflict();
        }
        if (repository.existsByNameIgnoreCase(name) && !roomType.getName().equalsIgnoreCase(name)) {
            throw nameConflict();
        }
        roomType.update(typeCode, name, request.description(), Instant.now());
        return repository.save(roomType);
    }

    @Transactional
    public void delete(long id) {
        repository.delete(get(id));
    }

    private void ensureTypeCodeAvailable(String typeCode) {
        if (repository.existsByTypeCodeIgnoreCase(typeCode)) {
            throw typeCodeConflict();
        }
    }

    private void ensureNameAvailable(String name) {
        if (repository.existsByNameIgnoreCase(name)) {
            throw nameConflict();
        }
    }

    private ConflictException typeCodeConflict() {
        return new ConflictException("Room type code already exists", "ROOM_TYPE_CODE_CONFLICT");
    }

    private ConflictException nameConflict() {
        return new ConflictException("Room type name already exists", "ROOM_TYPE_NAME_CONFLICT");
    }
}
