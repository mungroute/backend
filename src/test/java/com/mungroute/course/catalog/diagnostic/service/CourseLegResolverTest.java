package com.mungroute.course.catalog.diagnostic.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
