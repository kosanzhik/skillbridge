package kz.skillbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SkillbridgeApplication {

	public static void main(String[] args) {
		SpringApplication.run(SkillbridgeApplication.class, args);
	}

}
