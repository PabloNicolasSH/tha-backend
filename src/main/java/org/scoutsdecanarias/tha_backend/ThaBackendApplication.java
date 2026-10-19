package org.scoutsdecanarias.tha_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableAsync
@SpringBootApplication
public class ThaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ThaBackendApplication.class, args);
	}

}
