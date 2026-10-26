package fptu.semester8.mss301.propertiesservice.features.ward.application;

import fptu.semester8.mss301.propertiesservice.features.ward.domain.Ward;
import fptu.semester8.mss301.propertiesservice.features.ward.infrastructure.persistence.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class WardService {

    WardRepository repository;

    @Transactional
    public Ward create(Long p, String c, String n, String d) {
        return repository.save(new Ward(p, c.trim(), n.trim(), d));
    }

    @Transactional(readOnly = true)
    public List<Ward> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Ward get(long id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Ward not found: " + id));
    }

    @Transactional
    public Ward update(long id, Long p, String c, String n, String d) {
        Ward w = get(id);
        w.update(p, c.trim(), n.trim(), d);
        return repository.save(w);
    }

    @Transactional
    public void delete(long id) {
        repository.delete(get(id));
    }

    public List<Ward> getWardsByProvinceId(Long provinceId) {
        return repository.findWardsByProvinceId(provinceId);
    }
}
