package com.mungroute.weather.adapter;

import com.mungroute.course.port.CourseWeatherPort;
import com.mungroute.weather.service.LiveWeatherService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class CourseWeatherAdapter implements CourseWeatherPort {
    private final LiveWeatherService liveWeatherService;

    public CourseWeatherAdapter(LiveWeatherService liveWeatherService) {
        this.liveWeatherService = liveWeatherService;
    }

    @Override
    public Optional<Snapshot> resolve(Instant requestedAt) {
        return liveWeatherService.resolve(requestedAt).map(weather -> new Snapshot(
                weather.observedAt(),
                weather.airTemperatureC(),
                weather.windSpeedMps(),
                weather.solarRadiationWm2(),
                weather.source()
        ));
    }
}
