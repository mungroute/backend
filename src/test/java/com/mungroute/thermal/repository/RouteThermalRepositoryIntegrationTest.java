package com.mungroute.thermal.repository;

import com.mungroute.thermal.domain.ThermalReferenceTime;
import com.mungroute.thermal.service.RouteThermalService;
import com.mungroute.thermal.service.SelectedRouteTemperature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
@Sql(scripts = "/sql/route-network-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/route-network-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class RouteThermalRepositoryIntegrationTest {
    private static final long FIXTURE_SEGMENT_ID = 8100000000000000L;

    @Autowired
    RouteThermalRepository routeThermalRepository;

    @Autowired
    RouteThermalService routeThermalService;

    @Test
    void readsRepresentativeRouteSegmentFromPostgresql() {
        RouteThermalSnapshot snapshot = routeThermalRepository.findBySegmentId(FIXTURE_SEGMENT_ID).orElseThrow();

        assertThat(snapshot.surfaceTemp09C()).isEqualByComparingTo("29.83");
        assertThat(snapshot.surfaceTemp12C()).isEqualByComparingTo("49.44");
        assertThat(snapshot.surfaceTemp15C()).isEqualByComparingTo("55.55");
        assertThat(snapshot.surfaceTemp18C()).isEqualByComparingTo("33.85");
        assertThat(snapshot.surfaceTempPeakC()).isEqualByComparingTo("55.55");
        assertThat(snapshot.modelConfidence()).isEqualTo("LOW");
        assertThat(snapshot.weatherDate()).isEqualTo(LocalDate.of(2026, 8, 11));
        assertThat(snapshot.updatedAt()).isNotNull();

        SelectedRouteTemperature selected = routeThermalService.getTemperature(
                FIXTURE_SEGMENT_ID,
                OffsetDateTime.parse("2026-08-14T14:30:00+09:00")
        );
        assertThat(selected.referenceTime()).isEqualTo(ThermalReferenceTime.H15);
        assertThat(selected.temperatureC()).isEqualByComparingTo("55.55");
    }
}
