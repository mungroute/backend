package com.mungroute.course.catalog.diagnostic.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CourseRouteSlicer {
    private static final double EARTH_RADIUS_M = 6_371_000.0;
    private static final double EPSILON = 1e-9;

    private final ObjectMapper objectMapper;

    CourseRouteSlicer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<JsonNode> split(String routeGeoJson, List<BigDecimal> orderedLengths) {
        return split(routeGeoJson, orderedLengths, false);
    }

    List<JsonNode> split(String routeGeoJson, List<BigDecimal> orderedLengths, boolean reverseRoute) {
        if (routeGeoJson == null || orderedLengths == null || orderedLengths.isEmpty()) {
            return List.of();
        }
        List<RoutePoint> points = parsePoints(routeGeoJson);
        if (points.size() < 2) return List.of();
        if (reverseRoute) Collections.reverse(points);

        double requestedTotal = orderedLengths.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
        if (!Double.isFinite(requestedTotal) || requestedTotal <= 0) return List.of();

        double[] cumulativeRouteMeters = cumulativeMeters(points);
        double routeTotal = cumulativeRouteMeters[cumulativeRouteMeters.length - 1];
        if (!Double.isFinite(routeTotal) || routeTotal <= 0) return List.of();

        List<JsonNode> slices = new ArrayList<>(orderedLengths.size());
        double requestedBefore = 0;
        for (BigDecimal orderedLength : orderedLengths) {
            double requestedAfter = requestedBefore + orderedLength.doubleValue();
            double startM = routeTotal * requestedBefore / requestedTotal;
            double endM = routeTotal * requestedAfter / requestedTotal;
            slices.add(toGeoJson(slice(points, cumulativeRouteMeters, startM, endM)));
            requestedBefore = requestedAfter;
        }
        return List.copyOf(slices);
    }

    private List<RoutePoint> parsePoints(String routeGeoJson) {
        try {
            JsonNode route = objectMapper.readTree(routeGeoJson);
            String type = route.path("type").asText();
            JsonNode coordinates = route.path("coordinates");
            List<RoutePoint> points = new ArrayList<>();
            if ("LineString".equals(type)) {
                appendLine(points, coordinates);
            } else if ("MultiLineString".equals(type)) {
                coordinates.forEach(line -> appendLine(points, line));
            }
            return points;
        } catch (JacksonException exception) {
            return List.of();
        }
    }

    private void appendLine(List<RoutePoint> target, JsonNode coordinates) {
        if (!coordinates.isArray()) return;
        coordinates.forEach(coordinate -> {
            if (!coordinate.isArray() || coordinate.size() < 2) return;
            RoutePoint point = new RoutePoint(coordinate.get(0).asDouble(), coordinate.get(1).asDouble());
            if (target.isEmpty() || !samePoint(target.getLast(), point)) target.add(point);
        });
    }

    private double[] cumulativeMeters(List<RoutePoint> points) {
        double[] cumulative = new double[points.size()];
        for (int index = 1; index < points.size(); index++) {
            cumulative[index] = cumulative[index - 1] + distance(points.get(index - 1), points.get(index));
        }
        return cumulative;
    }

    private List<RoutePoint> slice(
            List<RoutePoint> points,
            double[] cumulativeMeters,
            double startM,
            double endM
    ) {
        List<RoutePoint> sliced = new ArrayList<>();
        appendDistinct(sliced, interpolate(points, cumulativeMeters, startM));
        for (int index = 1; index < points.size() - 1; index++) {
            if (cumulativeMeters[index] > startM + EPSILON && cumulativeMeters[index] < endM - EPSILON) {
                appendDistinct(sliced, points.get(index));
            }
        }
        appendDistinct(sliced, interpolate(points, cumulativeMeters, endM));
        return sliced;
    }

    private RoutePoint interpolate(List<RoutePoint> points, double[] cumulativeMeters, double targetM) {
        if (targetM <= 0) return points.getFirst();
        double total = cumulativeMeters[cumulativeMeters.length - 1];
        if (targetM >= total) return points.getLast();
        for (int index = 1; index < cumulativeMeters.length; index++) {
            if (targetM <= cumulativeMeters[index]) {
                double segmentStart = cumulativeMeters[index - 1];
                double segmentLength = cumulativeMeters[index] - segmentStart;
                double ratio = segmentLength <= EPSILON ? 0 : (targetM - segmentStart) / segmentLength;
                RoutePoint from = points.get(index - 1);
                RoutePoint to = points.get(index);
                return new RoutePoint(
                        from.lon() + (to.lon() - from.lon()) * ratio,
                        from.lat() + (to.lat() - from.lat()) * ratio
                );
            }
        }
        return points.getLast();
    }

    private JsonNode toGeoJson(List<RoutePoint> points) {
        ObjectNode route = objectMapper.createObjectNode();
        route.put("type", "LineString");
        ArrayNode coordinates = route.putArray("coordinates");
        points.forEach(point -> {
            ArrayNode coordinate = coordinates.addArray();
            coordinate.add(point.lon());
            coordinate.add(point.lat());
        });
        return route;
    }

    private void appendDistinct(List<RoutePoint> target, RoutePoint point) {
        if (target.isEmpty() || !samePoint(target.getLast(), point)) target.add(point);
    }

    private boolean samePoint(RoutePoint left, RoutePoint right) {
        return Math.abs(left.lon() - right.lon()) < EPSILON
                && Math.abs(left.lat() - right.lat()) < EPSILON;
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
}
