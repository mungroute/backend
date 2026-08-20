package com.mungroute.weather.client;

public class WeatherClientException extends RuntimeException {
    public WeatherClientException(String message) {
        super(message);
    }

    public WeatherClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
