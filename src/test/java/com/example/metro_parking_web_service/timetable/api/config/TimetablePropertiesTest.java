/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TimetablePropertiesTest {

    @EnableConfigurationProperties({
        TimetableProviderProperties.class,
        TimetableSnapshotProperties.class
    })
    static class TestConfiguration {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfiguration.class)
                    .withPropertyValues(
                            "providers.tfnsw.timetable.api.sydney-trains=/trains",
                            "providers.tfnsw.timetable.api.metro=/metro",
                            "providers.tfnsw.timetable.enabled=true",
                            "timetable.snapshot.bootstrap-enabled=false",
                            "timetable.snapshot.sydney-trains-zip=trains.zip",
                            "timetable.snapshot.metro-zip=metro.zip",
                            "timetable.snapshot.maximum-compressed-package-bytes=2097152");

    @Test
    void bindsProviderFeedPathsAndEnabledFlag() {
        runner.run(
                context -> {
                    var properties = context.getBean(TimetableProviderProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.api().sydneyTrains()).isEqualTo("/trains");
                    assertThat(properties.api().metro()).isEqualTo("/metro");
                });
    }

    @Test
    void bindsBootstrapPathsAndPackageSizeLimit() {
        runner.run(
                context -> {
                    var properties = context.getBean(TimetableSnapshotProperties.class);
                    assertThat(properties.bootstrapEnabled()).isFalse();
                    assertThat(properties.sydneyTrainsZip().toString()).isEqualTo("trains.zip");
                    assertThat(properties.metroZip().toString()).isEqualTo("metro.zip");
                    assertThat(properties.maximumCompressedPackageBytes()).isEqualTo(2_097_152);
                });
    }
}
