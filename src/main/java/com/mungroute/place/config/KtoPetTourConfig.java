package com.mungroute.place.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(KtoPetTourProperties.class)
public class KtoPetTourConfig {

    @Bean
    RestClient ktoPetTourRestClient(KtoPetTourProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }
}
