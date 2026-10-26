package fptu.semester8.mss301.propertiesservice.config;

import org.springframework.context.annotation.*;
import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class OccupancyClockConfig {
    @Bean
    public Clock occupancyClock() { return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh")); }
}
