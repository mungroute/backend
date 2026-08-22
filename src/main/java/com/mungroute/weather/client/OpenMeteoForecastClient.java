package com.mungroute.weather.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mungroute.weather.config.HourlyForecastProperties;
import com.mungroute.weather.domain.ForecastWeather;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class OpenMeteoForecastClient implements ForecastWeatherGateway {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter HOURLY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final HourlyForecastProperties properties;

    public OpenMeteoForecastClient(
            RestClient hourlyForecastRestClient,
            HourlyForecastProperties properties
    ) {
        this.restClient = hourlyForecastRestClient;
        this.objectMapper = new ObjectMapper();
        this.properties = properties;
    }

    @Override
    public ForecastWeather fetch(Instant requestedAt) {
        if (requestedAt == null) throw new IllegalArgumentException("예보 시각은 필수입니다.");
        LocalDate targetDate = requestedAt.atZone(SERVICE_ZONE).toLocalDate();
        try {
            String body = restClient.get()
                    .uri(uri -> uri.path("/v1/forecast")
                            .queryParam("latitude", properties.latitude())
                            .queryParam("longitude", properties.longitude())
                            .queryParam("hourly", "temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m,shortwave_radiation")
                            .queryParam("wind_speed_unit", "ms")
                            .queryParam("timezone", SERVICE_ZONE.getId())
                            .queryParam("start_date", targetDate)
                            .queryParam("end_date", targetDate)
                            .build())
                    .retrieve()
                    .body(String.class);
            return parse(body, requestedAt);
        } catch (RestClientException exception) {
            throw new WeatherClientException("오늘 시간별 기상 예보를 가져오지 못했습니다.", exception);
        }
    }

    ForecastWeather parse(String body, Instant requestedAt) {
        try {
            JsonNode hourly = objectMapper.readTree(body).path("hourly");
            JsonNode times = hourly.path("time");
            String target = HOURLY_TIME.format(requestedAt.atZone(SERVICE_ZONE));
            int index = -1;
            for (int current = 0; current < times.size(); current++) {
                if (target.equals(times.get(current).asText())) {
                    index = current;
                    break;
                }
            }
            if (index < 0) throw new WeatherClientException("요청한 시각의 기상 예보가 없습니다.");
            return new ForecastWeather(
                    LocalDateTime.parse(times.get(index).asText(), HOURLY_TIME).atZone(SERVICE_ZONE).toInstant(),
                    required(hourly, "temperature_2m", index),
                    Math.max(0.0, required(hourly, "wind_speed_10m", index)),
                    Math.max(0.0, required(hourly, "shortwave_radiation", index)),
                    Math.max(0.0, required(hourly, "precipitation", index)),
                    bounded(required(hourly, "relative_humidity_2m", index), 0.0, 100.0)
            );
        } catch (WeatherClientException exception) {
            throw exception;
        } catch (RuntimeException | java.io.IOException exception) {
            throw new WeatherClientException("시간별 기상 예보 응답을 해석하지 못했습니다.", exception);
        }
    }

    private static double required(JsonNode hourly, String field, int index) {
        JsonNode value = hourly.path(field).path(index);
        double parsed = value.asDouble(Double.NaN);
        if (!Double.isFinite(parsed)) throw new WeatherClientException("기상 예보의 " + field + " 값이 없습니다.");
        return parsed;
    }

    private static double bounded(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
