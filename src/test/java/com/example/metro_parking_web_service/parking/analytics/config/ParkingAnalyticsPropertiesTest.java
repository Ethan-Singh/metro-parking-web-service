/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ParkingAnalyticsPropertiesTest {

    @EnableConfigurationProperties(ParkingAnalyticsProperties.class)
    static class TestConfig {}

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class)
                    .withPropertyValues(
                            "external-server.parking.analytics.almost-full-threshold=0.1",
                            "external-server.parking.analytics.history-weeks=4");

    @Test
    void shouldBindConfigurationPropertiesCorrectly() {
        contextRunner.run(
                context -> {
                    ParkingAnalyticsProperties props =
                            context.getBean(ParkingAnalyticsProperties.class);

                    assertThat(props.almostFullThreshold()).isEqualTo(0.1);
                    assertThat(props.historyWeeks()).isEqualTo(4);
                });
    }
}
