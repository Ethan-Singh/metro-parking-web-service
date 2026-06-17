/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ParkingSecurityPropertiesTest {

    @EnableConfigurationProperties(ParkingSecurityProperties.class)
    static class TestConfig {}

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class)
                    .withPropertyValues(
                            "external-server.parking.security.allowed-origins[0]=http://localhost:3000",
                            "external-server.parking.security.rate-limit.capacity=25",
                            "external-server.parking.security.rate-limit.refill-tokens=10",
                            "external-server.parking.security.rate-limit.refill-period-seconds=60");

    @Test
    void shouldBindConfigurationPropertiesCorrectly() {
        contextRunner.run(
                context -> {
                    ParkingSecurityProperties props =
                            context.getBean(ParkingSecurityProperties.class);

                    assertThat(props.allowedOrigins()).containsExactly("http://localhost:3000");
                    assertThat(props.rateLimit().capacity()).isEqualTo(25);
                    assertThat(props.rateLimit().refillTokens()).isEqualTo(10);
                    assertThat(props.rateLimit().refillPeriodSeconds()).isEqualTo(60);
                });
    }
}
