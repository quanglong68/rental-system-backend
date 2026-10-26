package fptu.semester8.mss301.propertiesservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PropertiesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PropertiesServiceApplication.class, args);
    }

}
