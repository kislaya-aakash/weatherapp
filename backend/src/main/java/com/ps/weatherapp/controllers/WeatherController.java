package com.ps.weatherapp.controllers;

import com.ps.weatherapp.models.*;
import com.ps.weatherapp.services.CityWeatherService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {

    private final CityWeatherService weatherService;

    public WeatherController(CityWeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/advice")
    public Mono<ResponseEntity<CityWeatherAdvice>> getWeatherAdvice(@RequestParam String city) {
        return weatherService.getWeatherAdvice(city)
            .map(cityWeatherAdvice -> {
                // Customizing the response body and status code
                if (cityWeatherAdvice.getStatus() == 200) {
                    // Successful response
                    return ResponseEntity.ok(cityWeatherAdvice); // 200 OK
                } else {
                    // Error response (e.g., city not found or service error)
                    HttpStatus status = cityWeatherAdvice.getStatus() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.SERVICE_UNAVAILABLE;
                    return ResponseEntity.status(status) // 404 Not Found
                            .body(cityWeatherAdvice);
                }
            })
            .onErrorResume(error -> {
                // Handle unexpected errors (e.g., network issues)
                CityWeatherAdvice fallbackPrediction = new CityWeatherAdvice(
                        "Service temporarily unavailable.", new LinkedHashMap<>(), 503);
                return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(fallbackPrediction)); // 503 Service Unavailable
            });
    }
}
