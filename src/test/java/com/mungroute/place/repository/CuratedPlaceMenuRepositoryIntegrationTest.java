package com.mungroute.place.repository;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class CuratedPlaceMenuRepositoryIntegrationTest {
    @Autowired
    CuratedPlaceMenuRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManagerFactory entityManagerFactory;

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

    @Test
    void loadsItemsInDisplayOrderThenIdWithOneQuery() {
        String contentId = "jpa-order-test";
        jdbcTemplate.update("""
                INSERT INTO curated_place_menu(
                    content_id, place_name, source_label, verified_on
                ) VALUES (?, 'JPA 정렬 테스트', '테스트', DATE '2026-08-24')
                """, contentId);
        jdbcTemplate.update("""
                INSERT INTO curated_place_menu_item(
                    content_id, menu_name, is_specialty, display_order
                ) VALUES (?, '두 번째', false, 2),
                         (?, '첫 번째-A', true, 1),
                         (?, '첫 번째-B', false, 1)
                """, contentId, contentId, contentId);

        Statistics statistics = statistics();
        statistics.clear();

        CuratedPlaceMenuRepository.CuratedPlaceMenu menu = repository.findByContentId(contentId)
                .orElseThrow();

        assertThat(menu.items())
                .extracting(CuratedPlaceMenuRepository.CuratedMenuItem::name)
                .containsExactly("첫 번째-A", "첫 번째-B", "두 번째");
        assertThat(statistics.getPrepareStatementCount()).isOne();
    }

    @Test
    void returnsParentWithNoItemsUsingOneQuery() {
        Statistics statistics = statistics();
        statistics.clear();

        CuratedPlaceMenuRepository.CuratedPlaceMenu menu = repository.findByContentId("1684190613")
                .orElseThrow();

        assertThat(menu.placeName()).isEqualTo("프로젝트스페이스 가제");
        assertThat(menu.items()).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isOne();
    }

    @Test
    void returnsEmptyForUnknownContentIdUsingOneQuery() {
        Statistics statistics = statistics();
        statistics.clear();

        assertThat(repository.findByContentId("not-registered")).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isOne();
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
