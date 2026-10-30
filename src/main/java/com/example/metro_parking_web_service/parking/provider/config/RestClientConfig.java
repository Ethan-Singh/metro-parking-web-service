/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.provider.config;

import com.example.metro_parking_web_service.provider.tfnsw.config.TfnswConnectionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
@Slf4j
public class RestClientConfig {

    @Bean("parkingRestClient")
    @Primary
    RestClient parkingRestClient(
            TfnswConnectionProperties tfnswProperties, CarParkApiProperties parkingProperties) {
        String baseUrl = tfnswProperties.baseUrl();
        String api = parkingProperties.api();
        String url = (baseUrl == null ? "" : baseUrl) + (api == null ? "" : api);

        log.info(
                "event=parking_provider_client_configured baseUri={} authenticationConfigured={}",
                url,
                tfnswProperties.apikey() != null && !tfnswProperties.apikey().isBlank());

        RestClient.Builder builder = RestClient.builder();
        if (!url.isBlank()) {
            builder.baseUrl(url);
        }
        if (tfnswProperties.apikey() != null && !tfnswProperties.apikey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, tfnswProperties.apikey());
        }
        return builder.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
    }
}
