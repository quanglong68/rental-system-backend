package fptu.semester8.mss301.propertiesservice.common.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeIdGeneratorTest {
    @Test
    void generatesPositiveUniqueLongIds() {
        IdGenerator generator = new SnowflakeIdGenerator(0);
        Set<Long> ids = new HashSet<>();

        for (int i = 0; i < 1_000; i++) {
            long id = generator.nextId();
            assertTrue(id > 0);
            ids.add(id);
        }

        assertEquals(1_000, ids.size());
    }
}
