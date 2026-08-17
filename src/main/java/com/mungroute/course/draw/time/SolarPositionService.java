package com.mungroute.course.draw.time;

import com.mungroute.thermal.domain.ThermalReferenceTime;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class SolarPositionService {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final double SUNRISE_SUNSET_ELEVATION_DEG = -0.833;

    public CourseCalculationContext resolve(Instant requestedAt, double lat, double lon) {
        Instant calculatedAt = requestedAt == null ? Instant.now() : requestedAt;
        ZonedDateTime local = calculatedAt.atZone(SERVICE_ZONE);
        double elevation = solarElevationDeg(local, lat, lon);
        SolarState solarState = elevation > SUNRISE_SUNSET_ELEVATION_DEG
                ? SolarState.DAYLIGHT
                : SolarState.NIGHT;
        ThermalReferenceTime referenceTime = ThermalReferenceTime.nearestTo(LocalTime.from(local));
        return new CourseCalculationContext(calculatedAt, referenceTime, solarState, elevation);
    }

    // NOAA의 fractional-year 근사식을 사용한다. D3 pvlib 결과를 테스트 fixture로 교차 검증한다.
    static double solarElevationDeg(ZonedDateTime local, double lat, double lon) {
        int daysInYear = local.toLocalDate().lengthOfYear();
        double localHour = local.getHour() + local.getMinute() / 60.0 + local.getSecond() / 3600.0;
        double gamma = 2.0 * Math.PI / daysInYear
                * (local.getDayOfYear() - 1 + (localHour - 12.0) / 24.0);
        double equationOfTimeMinutes = 229.18 * (
                0.000075
                        + 0.001868 * Math.cos(gamma)
                        - 0.032077 * Math.sin(gamma)
                        - 0.014615 * Math.cos(2 * gamma)
                        - 0.040849 * Math.sin(2 * gamma)
        );
        double declination = 0.006918
                - 0.399912 * Math.cos(gamma)
                + 0.070257 * Math.sin(gamma)
                - 0.006758 * Math.cos(2 * gamma)
                + 0.000907 * Math.sin(2 * gamma)
                - 0.002697 * Math.cos(3 * gamma)
                + 0.00148 * Math.sin(3 * gamma);
        double utcOffsetHours = local.getOffset().getTotalSeconds() / 3600.0;
        double timeOffsetMinutes = equationOfTimeMinutes + 4.0 * lon - 60.0 * utcOffsetHours;
        double trueSolarMinutes = localHour * 60.0 + timeOffsetMinutes;
        double hourAngleDeg = trueSolarMinutes / 4.0 - 180.0;
        while (hourAngleDeg < -180.0) hourAngleDeg += 360.0;
        while (hourAngleDeg > 180.0) hourAngleDeg -= 360.0;

        double latitudeRad = Math.toRadians(lat);
        double hourAngleRad = Math.toRadians(hourAngleDeg);
        double cosineZenith = Math.sin(latitudeRad) * Math.sin(declination)
                + Math.cos(latitudeRad) * Math.cos(declination) * Math.cos(hourAngleRad);
        cosineZenith = Math.max(-1.0, Math.min(1.0, cosineZenith));
        return 90.0 - Math.toDegrees(Math.acos(cosineZenith));
    }
}
