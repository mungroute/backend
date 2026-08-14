package com.mungroute.thermal.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.thermal.domain.ThermalReferenceTime;
import com.mungroute.thermal.exception.ThermalErrorCode;
import com.mungroute.thermal.repository.RouteThermalRepository;
import com.mungroute.thermal.repository.RouteThermalSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Service
public class RouteThermalService {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final RouteThermalRepository routeThermalRepository;

    public RouteThermalService(RouteThermalRepository routeThermalRepository) {
        this.routeThermalRepository = routeThermalRepository;
    }

    @Transactional(readOnly = true)
    public SelectedRouteTemperature getTemperature(Long segmentId, OffsetDateTime requestedAt) {
        Objects.requireNonNull(requestedAt, "requestedAt은 필수입니다.");
        LocalTime requestedLocalTime = requestedAt.atZoneSameInstant(SERVICE_ZONE).toLocalTime();
        ThermalReferenceTime referenceTime = ThermalReferenceTime.nearestTo(requestedLocalTime);
        RouteThermalSnapshot snapshot = routeThermalRepository.findBySegmentId(segmentId)
                .orElseThrow(() -> new BusinessException(ThermalErrorCode.ROUTE_SEGMENT_NOT_FOUND));
        BigDecimal selectedTemperature = snapshot.temperatureAt(referenceTime);
        if (selectedTemperature == null
                || snapshot.surfaceTempPeakC() == null
                || snapshot.modelConfidence() == null
                || snapshot.weatherDate() == null
                || snapshot.updatedAt() == null) {
            throw new BusinessException(ThermalErrorCode.THERMAL_DATA_UNAVAILABLE);
        }
        return new SelectedRouteTemperature(
                snapshot.segmentId(),
                requestedAt,
                requestedLocalTime,
                referenceTime,
                selectedTemperature,
                snapshot.surfaceTempPeakC(),
                snapshot.modelConfidence(),
                snapshot.weatherDate(),
                snapshot.updatedAt()
        );
    }
}
