package com.solvelify.routeplanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RoutePlannerApplication {

	public static void main(String[] args) {
		SpringApplication.run(RoutePlannerApplication.class, args);
	}

}
