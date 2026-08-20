package com.mungroute.weather.client;

import com.mungroute.weather.config.KmaAsosProperties;
import com.mungroute.weather.domain.AsosObservation;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Component
public class KmaAsosClient implements AsosWeatherGateway {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter OBSERVED_AT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final double MJ_PER_M2_TO_W_PER_M2 = 1_000_000.0 / 3_600.0;

    private final RestClient restClient;
    private final KmaAsosProperties properties;

    public KmaAsosClient(RestClient kmaAsosRestClient, KmaAsosProperties properties) {
        this.restClient = kmaAsosRestClient;
        this.properties = properties;
    }

    @Override
    public AsosObservation fetchLatest() {
        if (!configured()) throw new WeatherClientException("기상청 API허브 인증키가 설정되지 않았습니다.");
        try {
            String body = restClient.get()
                    .uri(uri -> uri.path("/api/typ01/url/kma_sfctm2.php")
                            .queryParam("stn", properties.stationId())
                            .queryParam("help", 0)
                            .queryParam("authKey", properties.authKey())
                            .build())
                    .retrieve()
                    .body(String.class);
            return parse(body);
        } catch (RestClientException exception) {
            throw new WeatherClientException("기상청 ASOS 관측값을 가져오지 못했습니다.", exception);
        }
    }

    @Override
    public boolean configured() {
        return properties.configured();
    }

    AsosObservation parse(String body) {
        if (body == null || body.isBlank()) {
            throw new WeatherClientException("기상청 ASOS 응답이 비어 있습니다.");
        }
        String dataLine = Arrays.stream(body.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new WeatherClientException("기상청 ASOS 응답에 관측 행이 없습니다."));
        String[] fields = dataLine.split("\\s+");
        if (fields.length < 35) {
            throw new WeatherClientException("기상청 ASOS 관측 행의 필드 수가 부족합니다.");
        }
        try {
            LocalDateTime observedAt = LocalDateTime.parse(fields[0], OBSERVED_AT);
            return new AsosObservation(
                    observedAt.atZone(SERVICE_ZONE).toInstant(),
                    Integer.parseInt(fields[1]),
                    temperature(fields[11]),
                    nonNegativeRequired(fields[3], "풍속"),
                    nonNegativeRequired(fields[34], "일사") * MJ_PER_M2_TO_W_PER_M2,
                    Math.max(0.0, optional(fields[15])),
                    Math.max(0.0, optional(fields[18])),
                    optional(fields[13])
            );
        } catch (RuntimeException exception) {
            if (exception instanceof WeatherClientException weatherClientException) throw weatherClientException;
            throw new WeatherClientException("기상청 ASOS 관측 행을 해석하지 못했습니다.", exception);
        }
    }

    private static double temperature(String value) {
        double parsed = Double.parseDouble(value);
        if (!Double.isFinite(parsed) || parsed < -80.0 || parsed > 60.0) {
            throw new WeatherClientException("기상청 ASOS 기온 관측값이 올바르지 않습니다.");
        }
        return parsed;
    }

    private static double nonNegativeRequired(String value, String label) {
        double parsed = Double.parseDouble(value);
        if (!Double.isFinite(parsed) || parsed < 0.0) {
            throw new WeatherClientException("기상청 ASOS " + label + " 관측값이 결측입니다.");
        }
        return parsed;
    }

    private static double optional(String value) {
        double parsed = Double.parseDouble(value);
        return !Double.isFinite(parsed) || parsed <= -9.0 ? 0.0 : parsed;
    }
}
