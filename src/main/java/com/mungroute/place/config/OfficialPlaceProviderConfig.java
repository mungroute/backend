package com.mungroute.place.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
        FoodSafetyPlaceProperties.class,
        KakaoLocalProperties.class
})
public class OfficialPlaceProviderConfig {

    @Bean
    RestClient foodSafetyPetRestaurantsRestClient(FoodSafetyPlaceProperties properties) {
        return RestClient.builder().baseUrl(properties.exportUrl()).build();
    }

    @Bean
    RestClient kakaoLocalRestClient(KakaoLocalProperties properties) {
        return RestClient.builder().baseUrl(properties.baseUrl()).build();
    }
}
