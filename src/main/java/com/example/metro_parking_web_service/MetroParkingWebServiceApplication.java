/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service;

import com.example.metro_parking_web_service.parking.server.config.ParkingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(ParkingProperties.class)
@EnableScheduling
public class MetroParkingWebServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetroParkingWebServiceApplication.class, args);
    }
}
