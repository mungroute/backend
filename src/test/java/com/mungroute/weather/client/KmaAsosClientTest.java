package com.mungroute.weather.client;

import com.mungroute.weather.config.KmaAsosProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class KmaAsosClientTest {

    @Test
    void parsesTheOfficialAsosTextFieldsAndConvertsSolarUnits() {
        KmaAsosClient client = new KmaAsosClient(
                RestClient.builder().baseUrl("https://example.test").build(),
                properties()
        );
        String[] fields = new String[46];
        Arrays.fill(fields, "-9");
        fields[0] = "202608201500";
        fields[1] = "108";
        fields[3] = "2.8";
        fields[11] = "31.3";
        fields[13] = "48";
        fields[15] = "0";
        fields[18] = "0";
        fields[34] = "3.08";

        var result = client.parse("# header\n" + String.join(" ", fields));

        assertThat(result.observedAt()).isEqualTo(Instant.parse("2026-08-20T06:00:00Z"));
        assertThat(result.stationId()).isEqualTo(108);
        assertThat(result.airTemperatureC()).isEqualTo(31.3);
        assertThat(result.windSpeedMps()).isEqualTo(2.8);
        assertThat(result.solarRadiationWm2()).isCloseTo(855.56, org.assertj.core.data.Offset.offset(0.01));
    }

    private static KmaAsosProperties properties() {
        return new KmaAsosProperties(
                "https://example.test", "test-key", 108,
                Duration.ofMinutes(10), Duration.ofHours(2),
                Duration.ofMinutes(1), Duration.ofMinutes(90)
        );
    }
}
