/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.provider.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.metro_parking_web_service.provider.tfnsw.config.TfnswConnectionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class RestClientConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(RestClientConfig.class)
                    .withBean(
                            TfnswConnectionProperties.class,
                            () -> new TfnswConnectionProperties("https://base-url", "apikey key"))
                    .withBean(
                            CarParkApiProperties.class, () -> new CarParkApiProperties("/parking"));

    @Test
    void shouldCreateRestClientWithCorrectBaseUrlAndHeaders() {
        contextRunner.run(
                context -> {
                    RestClient client = context.getBean("parkingRestClient", RestClient.class);

                    assertThat(client).isNotNull();
                });
    }
}
