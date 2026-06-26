/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.metro_parking_web_service.parking.analytics.dto.ParkingHistoryResponse;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CacheConfigTest {

    @Autowired private Cache<String, ParkingHistoryResponse> historyCache;

    @Test
    void shouldLoadHistoryCacheBean() {
        assertThat(historyCache).isNotNull();
    }

    @Test
    void shouldStoreAndRetrieveValue() {
        String key = "1|2026-01-01|2026-01-02|HOURLY";
        ParkingHistoryResponse value =
                new ParkingHistoryResponse("test", null, null, java.util.List.of());

        historyCache.put(key, value);

        ParkingHistoryResponse cached = historyCache.getIfPresent(key);

        assertThat(cached).isEqualTo(value);
    }

    @Test
    void shouldEvictValueWhenExpiredOrMissingBehavior() {
        String key = "missing-key";

        assertThat(historyCache.getIfPresent(key)).isNull();
    }
}
