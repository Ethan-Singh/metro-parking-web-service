/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableMongoAuditing
public class MetroParkingWebServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetroParkingWebServiceApplication.class, args);
    }
}
