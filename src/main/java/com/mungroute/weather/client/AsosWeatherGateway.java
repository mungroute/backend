package com.mungroute.weather.client;

import com.mungroute.weather.domain.AsosObservation;

public interface AsosWeatherGateway {
    AsosObservation fetchLatest();

    boolean configured();
}
