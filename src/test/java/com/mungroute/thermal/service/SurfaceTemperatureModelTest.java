package com.mungroute.thermal.service;

import com.mungroute.thermal.domain.SurfaceTemperatureModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SurfaceTemperatureModelTest {
    private final SurfaceTemperatureModel model = new SurfaceTemperatureModel();

    @Test
    void matchesTheExistingPythonThermalModelFixture() {
        double result = model.solve(
                0.12,
                0.95,
                0.30,
                31.3,
                2.8,
                855.5555555555555,
                0.517,
                91.9899202097912
        );

        assertThat(result).isCloseTo(52.01, org.assertj.core.data.Offset.offset(0.01));
    }
}
