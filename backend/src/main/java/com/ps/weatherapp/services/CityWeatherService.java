package com.ps.weatherapp.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ps.weatherapp.exceptions.CityWeatherException;
import com.ps.weatherapp.interfaces.ICityWeatherService;
import com.ps.weatherapp.models.*;
import com.ps.weatherapp.utilities.CacheManager;

import com.ps.weatherapp.utilities.DateTimeManager;
import com.ps.weatherapp.utilities.WeatherAdviceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.util.*;

@Service
public class CityWeatherService implements ICityWeatherService {

    private final WebClient webClient;
    private Map<String, CityWeatherDetails> weatherBackUpData;
    private final CacheManager<CityWeatherDetails> cacheManager;
    private static final Logger logger = LoggerFactory.getLogger(CityWeatherService.class);

    @Value("${weather.api.key}")
    private String apiKey;

    @Value("${weather.api.count}")
    private int count;

    @Value("${weather.api.service.type}")
    private String serviceType;

    @Value("${service.online}")
    private boolean isOnline;

    @Value("${service.cacheFileName}")
    private String fileName;

    public CityWeatherService(WebClient webClient, CacheManager<CityWeatherDetails> cacheManager) {
        this.webClient = webClient;
        this.weatherBackUpData = new HashMap<>();
        this.cacheManager = cacheManager;
    }

    public Mono<CityWeatherPrediction> getWeatherPrediction(String city) {
        // Load the weather data from file
        weatherBackUpData = cacheManager.loadCacheFromFile(fileName, new TypeReference<>() {});

        // Handle offline mode
        if (!isOnline) {
            logger.info("Service is in offline mode. Fetching data from back up.");
            return getPredictionFromCache(city)
                    .switchIfEmpty(Mono.just(new CityWeatherPrediction("No data available currently for ".concat(city), new LinkedHashMap<>(), 500)));
        }

        // Online mode: Fetch data from API with fallback to back up
        return fetchWeatherFromApi(city)
            .flatMap(cityWeatherDetails -> {
                // Return the appropriate weather prediction based on the API response
                if (cityWeatherDetails.getCod().equals("200")) {
                    // Successful API response
                    return Mono.just(generateWeatherAdvice(cityWeatherDetails));
                } else {
                    // Other errors: fallback to back up
                    return getPredictionFromCache(city)
                            .switchIfEmpty(Mono.just(new CityWeatherPrediction(cityWeatherDetails.getMessage(), new LinkedHashMap<>(), Integer.parseInt(cityWeatherDetails.getCod())
                            )));
                }
            })
            .onErrorResume(error -> {
                // Handle unexpected errors (e.g., network issues)
                logger.error(error.getMessage());
                return getPredictionFromCache(city)
                        .switchIfEmpty(Mono.just(new CityWeatherPrediction("Service temporarily unavailable.", new LinkedHashMap<>(), 503)));
            });
    }

    private Mono<CityWeatherPrediction> getPredictionFromCache(String city) {
        CityWeatherDetails cachedWeather = weatherBackUpData.get(city);
        if (cachedWeather != null) {
            return Mono.just(generateWeatherAdvice(cachedWeather));
        }
        return Mono.empty();
    }

    /**
     * Generate weather advice based on weather conditions
     */
    private CityWeatherPrediction generateWeatherAdvice(CityWeatherDetails cityWeatherDetails) {

        CityWeatherPrediction cityWeatherPrediction = new CityWeatherPrediction();

        //Map to store weather prediction for each day. Key is the day for which weather prediction is done
        Map<String, List<WeatherPrediction>> map = new LinkedHashMap<>();

        //Array list to store 3hr gap based weather for a given day.
        List<WeatherPrediction> datedList = new ArrayList<>();

        //Get city timeZone offset
        City cityDetails = cityWeatherDetails.getCity();
        int timeZone = cityDetails.getTimezone();

        //Get date from first weather data
        String currentDate = DateTimeManager.extractDateFromUnix(cityWeatherDetails.getList().getFirst().getDt(), timeZone);

        for (WeatherDetails weatherDetails : cityWeatherDetails.getList()) {

            //instantiate empty whether prediction
            WeatherPrediction prediction = new WeatherPrediction();

            //Get city date and time
            String cityDate = DateTimeManager.extractDateFromUnix(weatherDetails.getDt(), timeZone);
            String cityTime = DateTimeManager.extractTimeFromUnix(weatherDetails.getDt(), timeZone);

            //set time of weather prediction
            prediction.setTime(cityTime);

            //set temp in Celsius for weather prediction
            double tempCelsius = Math.round(weatherDetails.getMain().getTemp() - 273.15);
            prediction.setTemperature(tempCelsius);

            List<WeatherStatus> statusList = new ArrayList<>();
            StringBuilder advice = new StringBuilder();

            weatherDetails.getWeather().forEach(weather -> {
                WeatherStatus weatherStatus = new WeatherStatus();
                weatherStatus.setDescription(weather.getDescription());
                weatherStatus.setStatus(weather.getMain());
                statusList.add(weatherStatus);
                advice.append(WeatherAdviceManager.getWeatherAdvice(weather));
            });

            prediction.setWeather(statusList);

            Wind wind = weatherDetails.getWind();
            double windSpeed = wind.getSpeed();


            // Advice for high temperature
            if (tempCelsius > 40) {
                advice.append("Use sunscreen lotion. ");
            }

            // Advice for high winds
            if (windSpeed > 10) {
                advice.append("It’s too windy, watch out! ");
            }

            prediction.setAdvice(!advice.isEmpty()? advice.toString() : "No advice as of now!!,");

            /*check if the has date has changed then the weather prediction should
                be store in new list representing date for new day */
            if (!currentDate.equals(cityDate)) {
                datedList = new ArrayList<>();
                currentDate = cityDate;
            }
            datedList.add(prediction);
            map.put(cityDate, datedList);
        }
        cityWeatherPrediction.setMessage("success");
        cityWeatherPrediction.setData(map);
        cityWeatherPrediction.setStatus(200);
        return cityWeatherPrediction;
    }

    /**
     * Method to fetch weather data from the API.
     */
    public Mono<CityWeatherDetails> fetchWeatherFromApi(String city) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder
                .path(serviceType)
                .queryParam("q", city)
                .queryParam("cnt", count)
                .queryParam("appid", apiKey)
                .build())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> clientResponse.bodyToMono(CityWeatherDetails.class)
                .flatMap(errorDetails -> {
                    // Return a custom error with cod included
                    return Mono.error(new CityWeatherException(
                            errorDetails.getCod(),
                            errorDetails.getMessage()
                    ));
                }))
            .onStatus(HttpStatusCode::isError, clientResponse -> {
                logger.error("Unexpected error with status code: {}", clientResponse.statusCode().value());
                return Mono.error(new RuntimeException("Unexpected error with status code: " + clientResponse.statusCode().value()));
            })
            .bodyToMono(CityWeatherDetails.class)
            .doOnNext(cityWeatherDetails -> {
                if (cityWeatherDetails != null && "200".equals(cityWeatherDetails.getCod())
                        && cityWeatherDetails.getList() != null && !cityWeatherDetails.getList().isEmpty()) {
                    weatherBackUpData.put(city, cityWeatherDetails);
                    cacheManager.saveCacheToFile(fileName, weatherBackUpData);
                }
            })
            .onErrorResume(error -> {
                if (error instanceof CityWeatherException && ((CityWeatherException) error).getStatus().equals("404")) {
                    CityWeatherException customError = (CityWeatherException) error;
                    logger.error("Error fetching weather data for city {}: {} - {}", city, customError.getStatus(), customError.getMessage());
                    return Mono.just(new CityWeatherDetails(customError.getMessage(), Collections.emptyList(), customError.getStatus()));
                }
                logger.error("Error fetching weather data from API for city {}: {}", city, error.getMessage());
                return Mono.just(new CityWeatherDetails("Service temporarily unavailable.", Collections.emptyList()));
            });
    }
}

