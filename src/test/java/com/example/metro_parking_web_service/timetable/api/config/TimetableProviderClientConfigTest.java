/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.metro_parking_web_service.provider.tfnsw.config.TfnswConnectionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class TimetableProviderClientConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TimetableProviderClientConfig.class)
                    .withBean(
                            TfnswConnectionProperties.class,
                            () ->
                                    new TfnswConnectionProperties(
                                            "https://api.example.test", "apikey token"));

    @Test
    void createsNamedTimetableProviderRestClient() {
        runner.run(
                context -> {
                    assertThat(context).hasBean("timetableProviderRestClient");
                    assertThat(context.getBean("timetableProviderRestClient", RestClient.class))
                            .isNotNull();
                });
    }

    @Test
    void createsClientWithoutBlankOptionalConnectionSettings() {
        new ApplicationContextRunner()
                .withUserConfiguration(TimetableProviderClientConfig.class)
                .withBean(
                        TfnswConnectionProperties.class,
                        () -> new TfnswConnectionProperties("", ""))
                .run(
                        context -> {
                            assertThat(context).hasBean("timetableProviderRestClient");
                            assertThat(
                                            context.getBean(
                                                    "timetableProviderRestClient",
                                                    RestClient.class))
                                    .isNotNull();
                        });
    }
}
