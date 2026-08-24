package com.mungroute.course.catalog.diagnostic.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class CourseLegResolverTest {
    private final CourseLegResolver resolver = new CourseLegResolver(new ObjectMapper());

    @Test
    void groupsInternalRoadLinksBySavedWaypointConnections() {
        String route = """
                {"type":"LineString","coordinates":[
                  [126.000,37.000],[126.005,37.000],[126.010,37.000],
                  [126.015,37.000],[126.020,37.000],[126.025,37.000]
                ]}
                """;
        String waypoints = """
                [
                  {"snapped":{"lat":37.000,"lon":126.000}},
                  {"snapped":{"lat":37.000,"lon":126.010}},
                  {"snapped":{"lat":37.000,"lon":126.020}},
                  {"snapped":{"lat":37.000,"lon":126.025}}
                ]
                """;

        var legs = resolver.resolve(waypoints, route, List.of(
                new BigDecimal("5"), new BigDecimal("5"),
                new BigDecimal("3"), new BigDecimal("7"),
                new BigDecimal("5")
        ));

        assertThat(legs.legSequences()).containsExactly(1, 1, 2, 2, 3);
        assertThat(legs.reverseRoute()).isFalse();
    }

    @Test
    void recognizesLegacyRoutesStoredInTheOppositeDirection() {
        String route = """
                {"type":"LineString","coordinates":[
                  [126.025,37.000],[126.020,37.000],[126.015,37.000],
                  [126.010,37.000],[126.005,37.000],[126.000,37.000]
                ]}
                """;
        String waypoints = """
                [
                  {"snapped":{"lat":37.000,"lon":126.000}},
                  {"snapped":{"lat":37.000,"lon":126.010}},
                  {"snapped":{"lat":37.000,"lon":126.020}},
                  {"snapped":{"lat":37.000,"lon":126.025}}
                ]
                """;

        var legs = resolver.resolve(waypoints, route, List.of(
                new BigDecimal("5"), new BigDecimal("5"),
                new BigDecimal("3"), new BigDecimal("7"),
                new BigDecimal("5")
        ));

        assertThat(legs.legSequences()).containsExactly(1, 1, 2, 2, 3);
        assertThat(legs.reverseRoute()).isTrue();
    }

    @Test
    void recalculatesLegacyFullLinkLengthsFromActualWaypointGeometry() {
        String route = """
                {"type":"LineString","coordinates":[
                  [126.000000,37.000000],
                  [126.000000,37.001664],
                  [126.000000,37.003664]
                ]}
                """;
        String waypoints = """
                [
                  {"snapped":{"lat":37.000000,"lon":126.000000}},
                  {"snapped":{"lat":37.001664,"lon":126.000000}},
                  {"snapped":{"lat":37.003664,"lon":126.000000}}
                ]
                """;
        List<BigDecimal> legacyFullLinkLengths = List.of(
                new BigDecimal("439"),
                new BigDecimal("600")
        );

        var resolution = resolver.resolve(waypoints, route, legacyFullLinkLengths);
        var reconciled = resolver.reconcileWithGeometry(legacyFullLinkLengths, resolution);

        assertThat(resolution.legSequences()).containsExactly(1, 2);
        assertThat(reconciled.getFirst().doubleValue()).isCloseTo(185.0, offset(0.5));
        assertThat(reconciled.stream().mapToDouble(BigDecimal::doubleValue).sum())
                .isCloseTo(407.4, offset(0.7));
    }
}
