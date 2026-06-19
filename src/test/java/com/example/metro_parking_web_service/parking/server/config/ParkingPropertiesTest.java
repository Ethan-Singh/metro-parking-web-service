/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ParkingPropertiesTest {

    @EnableConfigurationProperties(ParkingProperties.class)
    static class TestConfig {}

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class)
                    .withPropertyValues(
                            "external-server.parking.base-url=https://your-base-url",
                            "external-server.parking.api=/carpark",
                            "external-server.parking.apikey=your-api-key");

    @Test
    void shouldBindConfigurationPropertiesCorrectly() {
        contextRunner.run(
                context -> {
                    ParkingProperties props = context.getBean(ParkingProperties.class);

                    assertThat(props.baseUrl()).isEqualTo("https://your-base-url");
                    assertThat(props.api()).isEqualTo("/carpark");
                    assertThat(props.apikey()).isEqualTo("your-api-key");
                });
    }
}
