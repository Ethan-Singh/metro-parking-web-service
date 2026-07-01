/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SecurityPropertiesTest {

    @Autowired private SecurityProperties props;

    @Test
    void shouldBindConfigurationPropertiesCorrectly() {
        assertThat(props.allowedOrigins()).containsExactly("http://localhost:3000");
        assertThat(props.rateLimit().capacity()).isEqualTo(25);
        assertThat(props.rateLimit().refillTokens()).isEqualTo(10);
        assertThat(props.rateLimit().refillPeriodSeconds()).isEqualTo(60);
    }
}
