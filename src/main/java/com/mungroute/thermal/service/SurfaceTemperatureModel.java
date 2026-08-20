package com.mungroute.thermal.service;

public final class SurfaceTemperatureModel {
    private static final double STEFAN_BOLTZMANN = 5.670374419e-8;
    private static final double SKY_OFFSET_C = 10.0;
    private static final double PARK_COOLING_MAX_C = 1.5;
    private static final double PARK_COOLING_DISTANCE_M = 200.0;

    public double solve(
            double albedo,
            double emissivity,
            double groundFluxRatio,
            double airTemperatureC,
            double windSpeedMps,
            double solarRadiationWm2,
            double svf,
            double parkProximityM
    ) {
        requireFinite(albedo, emissivity, groundFluxRatio, airTemperatureC, windSpeedMps,
                solarRadiationWm2, svf, parkProximityM);
        if (albedo < 0.0 || albedo > 1.0 || emissivity <= 0.0 || emissivity > 1.0) {
            throw new IllegalArgumentException("알베도 또는 방사율이 범위를 벗어났습니다.");
        }
        if (groundFluxRatio < 0.0 || groundFluxRatio > 1.0) {
            throw new IllegalArgumentException("지중열 비율이 범위를 벗어났습니다.");
        }
        double boundedSvf = clamp(svf, 0.0, 1.0);
        double low = airTemperatureC - 30.0;
        double high = airTemperatureC + 90.0;
        for (int iteration = 0; iteration < 64; iteration++) {
            double mid = (low + high) / 2.0;
            double residual = residual(
                    mid, albedo, emissivity, groundFluxRatio, airTemperatureC,
                    windSpeedMps, solarRadiationWm2, boundedSvf
            );
            if (residual > 0.0) low = mid;
            else high = mid;
        }
        double solved = (low + high) / 2.0;
        double cooling = PARK_COOLING_MAX_C * clamp(
                1.0 - Math.max(parkProximityM, 0.0) / PARK_COOLING_DISTANCE_M,
                0.0,
                1.0
        );
        return solved - cooling;
    }

    private static double residual(
            double surfaceTemperatureC,
            double albedo,
            double emissivity,
            double groundFluxRatio,
            double airTemperatureC,
            double windSpeedMps,
            double solarRadiationWm2,
            double svf
    ) {
        double surfaceK = surfaceTemperatureC + 273.15;
        double airK = airTemperatureC + 273.15;
        double skyK = Math.max(airTemperatureC - SKY_OFFSET_C, -80.0) + 273.15;
        double absorbed = (1.0 - albedo) * Math.max(solarRadiationWm2, 0.0);
        double available = absorbed * (1.0 - groundFluxRatio);
        double convectionCoefficient = 5.7 + 3.8 * Math.max(windSpeedMps, 0.0);
        double convection = convectionCoefficient * (surfaceTemperatureC - airTemperatureC);
        double longwave = emissivity * STEFAN_BOLTZMANN * (
                svf * (Math.pow(surfaceK, 4) - Math.pow(skyK, 4))
                        + (1.0 - svf) * (Math.pow(surfaceK, 4) - Math.pow(airK, 4))
        );
        return available - convection - longwave;
    }

    private static void requireFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("열모델 입력에 유한하지 않은 값이 있습니다.");
            }
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
