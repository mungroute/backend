package com.mungroute.weather.client;

import com.mungroute.weather.config.HourlyForecastProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OpenMeteoForecastClientTest {

    @Test
    void selectsTheRequestedKstHourAndKeepsSolarRadiationInWatts() {
        OpenMeteoForecastClient client = new OpenMeteoForecastClient(
                RestClient.builder().baseUrl("https://example.test").build(),
                new HourlyForecastProperties("https://example.test", 37.5665, 126.9780)
        );
        String body = """
                {
                  "hourly": {
                    "time": ["2026-08-21T15:00", "2026-08-21T18:00"],
                    "temperature_2m": [26.1, 24.8],
                    "relative_humidity_2m": [70, 78],
                    "precipitation": [0.0, 0.3],
                    "wind_speed_10m": [2.4, 3.1],
                    "shortwave_radiation": [210.0, 42.0]
                  }
                }
                """;

        var result = client.parse(body, Instant.parse("2026-08-21T09:00:00Z"));

        assertThat(result.validAt()).isEqualTo(Instant.parse("2026-08-21T09:00:00Z"));
        assertThat(result.airTemperatureC()).isEqualTo(24.8);
        assertThat(result.windSpeedMps()).isEqualTo(3.1);
        assertThat(result.solarRadiationWm2()).isEqualTo(42.0);
        assertThat(result.precipitationMm()).isEqualTo(0.3);
    }
}
