package com.ps.weatherapp.interfaces;

import com.ps.weatherapp.models.CityWeatherPrediction;
import reactor.core.publisher.Mono;

public interface ICityWeatherService {

    Mono<CityWeatherPrediction> getWeatherPrediction(String city);
}
