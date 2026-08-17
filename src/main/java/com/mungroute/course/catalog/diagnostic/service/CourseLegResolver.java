package com.mungroute.course.catalog.diagnostic.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CourseLegResolver {
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private final ObjectMapper objectMapper;

    CourseLegResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Resolution resolve(String waypointsJson, String routeGeoJson, List<BigDecimal> segmentLengths) {
        if (waypointsJson == null || routeGeoJson == null || segmentLengths == null || segmentLengths.isEmpty()) {
            return Resolution.empty();
        }
        try {
            List<RoutePoint> route = routePoints(objectMapper.readTree(routeGeoJson));
            List<RoutePoint> waypoints = waypointPoints(objectMapper.readTree(waypointsJson));
            if (route.size() < 2 || waypoints.size() < 2) return Resolution.empty();

            List<Integer> forward = resolveOrdered(route, waypoints, segmentLengths);
            if (!forward.isEmpty()) return new Resolution(forward, false);

            List<RoutePoint> reversed = new ArrayList<>(route);
            Collections.reverse(reversed);
            List<Integer> backward = resolveOrdered(reversed, waypoints, segmentLengths);
            return backward.isEmpty() ? Resolution.empty() : new Resolution(backward, true);
        } catch (JacksonException | IllegalArgumentException exception) {
            return Resolution.empty();
        }
    }

    private List<Integer> resolveOrdered(
            List<RoutePoint> route,
            List<RoutePoint> waypoints,
            List<BigDecimal> segmentLengths
    ) {
        double[] routeMeters = cumulativeMeters(route);
        double routeTotal = routeMeters[routeMeters.length - 1];
        double segmentTotal = segmentLengths.stream().mapToDouble(BigDecimal::doubleValue).sum();
        if (routeTotal <= 0 || segmentTotal <= 0) return List.of();

        List<Double> legEnds = locateLegEnds(route, routeMeters, waypoints);
        if (legEnds.size() != waypoints.size() - 1) return List.of();

        List<Integer> result = new ArrayList<>(segmentLengths.size());
        double segmentBefore = 0;
        for (BigDecimal segmentLength : segmentLengths) {
            double segmentAfter = segmentBefore + segmentLength.doubleValue();
            double midpointOnRoute = routeTotal * ((segmentBefore + segmentAfter) / 2.0) / segmentTotal;
            int legSequence = 1;
            while (legSequence < legEnds.size() && midpointOnRoute > legEnds.get(legSequence - 1)) {
                legSequence++;
            }
            result.add(legSequence);
            segmentBefore = segmentAfter;
        }
        return List.copyOf(result);
    }

    private List<Double> locateLegEnds(
            List<RoutePoint> route,
            double[] routeMeters,
            List<RoutePoint> waypoints
    ) {
        List<Double> ends = new ArrayList<>(waypoints.size() - 1);
        int searchFrom = 0;
        for (int waypointIndex = 1; waypointIndex < waypoints.size(); waypointIndex++) {
            int bestIndex = searchFrom;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (int routeIndex = searchFrom; routeIndex < route.size(); routeIndex++) {
                double candidateDistance = distance(waypoints.get(waypointIndex), route.get(routeIndex));
                if (candidateDistance < bestDistance) {
                    bestDistance = candidateDistance;
                    bestIndex = routeIndex;
                }
            }
            if (!Double.isFinite(bestDistance) || bestDistance > 35.0) return List.of();
            ends.add(routeMeters[bestIndex]);
            searchFrom = bestIndex;
        }
        ends.set(ends.size() - 1, routeMeters[routeMeters.length - 1]);
        return List.copyOf(ends);
    }

    private List<RoutePoint> waypointPoints(JsonNode waypoints) {
        if (!waypoints.isArray()) return List.of();
        List<RoutePoint> result = new ArrayList<>();
        waypoints.forEach(waypoint -> {
            JsonNode snapped = waypoint.path("snapped");
            result.add(new RoutePoint(snapped.path("lon").asDouble(), snapped.path("lat").asDouble()));
        });
        return result;
    }

    private List<RoutePoint> routePoints(JsonNode route) {
        String type = route.path("type").asText();
        JsonNode coordinates = route.path("coordinates");
        List<RoutePoint> result = new ArrayList<>();
        if ("LineString".equals(type)) {
            appendLine(result, coordinates);
        } else if ("MultiLineString".equals(type)) {
            coordinates.forEach(line -> appendLine(result, line));
        }
        return result;
    }

    private void appendLine(List<RoutePoint> target, JsonNode line) {
        if (!line.isArray()) return;
        line.forEach(coordinate -> {
            if (coordinate.isArray() && coordinate.size() >= 2) {
                RoutePoint point = new RoutePoint(coordinate.get(0).asDouble(), coordinate.get(1).asDouble());
                if (target.isEmpty() || !target.getLast().equals(point)) target.add(point);
            }
        });
    }

    private double[] cumulativeMeters(List<RoutePoint> points) {
        double[] cumulative = new double[points.size()];
        for (int index = 1; index < points.size(); index++) {
            cumulative[index] = cumulative[index - 1] + distance(points.get(index - 1), points.get(index));
        }
        return cumulative;
    }

    private double distance(RoutePoint from, RoutePoint to) {
        double lat1 = Math.toRadians(from.lat());
        double lat2 = Math.toRadians(to.lat());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(to.lon() - from.lon());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record RoutePoint(double lon, double lat) {
    }

    record Resolution(List<Integer> legSequences, boolean reverseRoute) {
        Resolution {
            legSequences = List.copyOf(legSequences);
        }

        static Resolution empty() {
            return new Resolution(List.of(), false);
        }
    }
}
