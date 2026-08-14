package com.mungroute.thermal.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ThermalReferenceTimeTest {
    @Test
    void mapsToNearestD5ReferenceTimeAndUsesLaterTimeAtTie() {
        assertThat(ThermalReferenceTime.nearestTo(LocalTime.MIDNIGHT)).isEqualTo(ThermalReferenceTime.H09);
        assertThat(ThermalReferenceTime.nearestTo(LocalTime.of(10, 29, 59))).isEqualTo(ThermalReferenceTime.H09);
        assertThat(ThermalReferenceTime.nearestTo(LocalTime.of(10, 30))).isEqualTo(ThermalReferenceTime.H12);
        assertThat(ThermalReferenceTime.nearestTo(LocalTime.of(13, 29, 59))).isEqualTo(ThermalReferenceTime.H12);
        assertThat(ThermalReferenceTime.nearestTo(LocalTime.of(13, 30))).isEqualTo(ThermalReferenceTime.H15);
        assertThat(ThermalReferenceTime.nearestTo(LocalTime.of(16, 29, 59))).isEqualTo(ThermalReferenceTime.H15);
        assertThat(ThermalReferenceTime.nearestTo(LocalTime.of(16, 30))).isEqualTo(ThermalReferenceTime.H18);
        assertThat(ThermalReferenceTime.nearestTo(LocalTime.of(23, 59))).isEqualTo(ThermalReferenceTime.H18);
    }
}
