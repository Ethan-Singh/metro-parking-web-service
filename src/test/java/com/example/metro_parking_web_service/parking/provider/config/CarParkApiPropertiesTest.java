/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.provider.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.metro_parking_web_service.provider.tfnsw.config.TfnswConnectionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CarParkApiPropertiesTest {

    @EnableConfigurationProperties({CarParkApiProperties.class, TfnswConnectionProperties.class})
    static class TestConfig {}

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class)
                    .withPropertyValues(
                            "providers.tfnsw.base-url=https://your-base-url",
                            "providers.tfnsw.apikey=apikey your-api-key",
                            "providers.tfnsw.parking.api=/carpark");

    @Test
    void shouldBindConfigurationPropertiesCorrectly() {
        contextRunner.run(
                context -> {
                    CarParkApiProperties props = context.getBean(CarParkApiProperties.class);
                    TfnswConnectionProperties connection =
                            context.getBean(TfnswConnectionProperties.class);

                    assertThat(connection.baseUrl()).isEqualTo("https://your-base-url");
                    assertThat(connection.apikey()).isEqualTo("apikey your-api-key");
                    assertThat(props.api()).isEqualTo("/carpark");
                });
    }
}
