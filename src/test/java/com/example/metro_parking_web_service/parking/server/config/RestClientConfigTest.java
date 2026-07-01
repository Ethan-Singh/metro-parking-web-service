/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class RestClientConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(RestClientConfig.class)
                    .withBean(
                            ParkingProperties.class,
                            () ->
                                    new ParkingProperties(
                                            "https://base-url", "/parking", "test-key"));

    @Test
    void shouldCreateRestClientWithCorrectBaseUrlAndHeaders() {
        contextRunner.run(
                context -> {
                    RestClient client = context.getBean("parkingRestClient", RestClient.class);

                    assertThat(client).isNotNull();
                });
    }
}
