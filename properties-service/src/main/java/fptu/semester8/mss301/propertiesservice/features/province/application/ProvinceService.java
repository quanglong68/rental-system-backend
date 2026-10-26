package fptu.semester8.mss301.propertiesservice.features.province.application;

import fptu.semester8.mss301.propertiesservice.features.province.domain.Province;
import fptu.semester8.mss301.propertiesservice.features.province.infrastructure.persistence.repository.ProvinceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProvinceService {
    private final ProvinceRepository repository;

    @Transactional
    public Province create(String c, String n, String d) {
        return repository.save(new Province(c.trim(), n.trim(), d));
    }

    @Transactional(readOnly = true)
    public List<Province> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Province get(long id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Province not found: " + id));
    }

    @Transactional
    public Province update(long id, String c, String n, String d) {
        Province p = get(id);
        p.update(c.trim(), n.trim(), d);
        return repository.save(p);
    }

    @Transactional
    public void delete(long id) {
        repository.delete(get(id));
    }
}
