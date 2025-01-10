package com.ps.weatherapp.controllers;

import com.ps.weatherapp.models.*;
import com.ps.weatherapp.services.CityWeatherService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;

@RestController
public class WeatherController {

    private final CityWeatherService weatherService;

    public WeatherController(CityWeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/weather")
    public Mono<ResponseEntity<CityWeatherPrediction>> getWeatherAdvice(@RequestParam String city) {
        return weatherService.getWeatherPrediction(city)
                .map(cityWeatherPrediction -> {
                    // Customizing the response body and status code
                    if (cityWeatherPrediction.getStatus() == 200) {
                        // Successful response
                        return ResponseEntity.ok(cityWeatherPrediction); // 200 OK
                    } else {
                        // Error response (e.g., city not found or service error)
                        HttpStatus status = cityWeatherPrediction.getStatus() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.SERVICE_UNAVAILABLE;
                        return ResponseEntity.status(status) // 404 Not Found
                                .body(cityWeatherPrediction);
                    }
                })
                .onErrorResume(error -> {
                    // Handle unexpected errors (e.g., network issues)
                    CityWeatherPrediction fallbackPrediction = new CityWeatherPrediction(
                            "Service temporarily unavailable.", new LinkedHashMap<>(), 500);
                    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(fallbackPrediction)); // 503 Service Unavailable
                });
    }
}
