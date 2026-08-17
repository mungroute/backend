package com.mungroute.course.draw.time;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SolarPositionServiceTest {
    private final SolarPositionService service = new SolarPositionService();

    @Test
    void classifiesSeoulSummerNoonAsDaylight() {
        var context = service.resolve(
                Instant.parse("2026-08-15T03:00:00Z"),
                37.5665,
                126.9780
        );

        assertThat(context.solarState()).isEqualTo(SolarState.DAYLIGHT);
        assertThat(context.solarElevationDeg()).isPositive();
        assertThat(context.referenceTime().time().getHour()).isEqualTo(12);
    }

    @Test
    void classifiesSeoulSummerNightAsNightAndKeepsTheLastReferenceScenario() {
        var context = service.resolve(
                Instant.parse("2026-08-15T12:00:00Z"),
                37.5665,
                126.9780
        );

        assertThat(context.solarState()).isEqualTo(SolarState.NIGHT);
        assertThat(context.shadeApplicable()).isFalse();
        assertThat(context.referenceTime().time().getHour()).isEqualTo(18);
    }
}
