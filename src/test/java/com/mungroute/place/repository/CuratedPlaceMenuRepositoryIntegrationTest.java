package com.mungroute.place.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class CuratedPlaceMenuRepositoryIntegrationTest {
    @Autowired
    CuratedPlaceMenuRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void migrationRegistersAllFortyKakaoPlacesAndTheirVerifiedMenus() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM curated_place_menu", Integer.class))
                .isEqualTo(40);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM curated_place_menu_item", Integer.class))
                .isGreaterThan(150);

        CuratedPlaceMenuRepository.CuratedPlaceMenu munei = repository.findByContentId("2557081428")
                .orElseThrow();
        assertThat(munei.placeName()).isEqualTo("무네이카페&바");
        assertThat(munei.items()).hasSize(8);
        assertThat(munei.items().getFirst().name()).isEqualTo("Sandcake");
        assertThat(munei.items().getFirst().priceWon()).isEqualTo(21_000L);

        CuratedPlaceMenuRepository.CuratedPlaceMenu cosia = repository.findByContentId("1853822140")
                .orElseThrow();
        assertThat(cosia.items()).extracting(CuratedPlaceMenuRepository.CuratedMenuItem::name)
                .contains("시그니처 브라운", "엔트로피", "초코 르뱅 쿠키");
        assertThat(cosia.items()).allMatch(item -> item.priceWon() == null);
    }
}
