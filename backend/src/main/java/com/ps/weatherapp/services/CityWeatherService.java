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

        // Create an object to store the final weather prediction result for the city
        CityWeatherPrediction cityWeatherPrediction = new CityWeatherPrediction();

        // Map to store weather predictions for each day. The key is the date, and the value is the list of predictions for that day
        Map<String, List<WeatherPrediction>> map = new LinkedHashMap<>();

        // List to store weather predictions for a specific day (in 3-hour intervals)
        List<WeatherPrediction> datedList = new ArrayList<>();

        // Get the city's time zone offset from the city details
        City cityDetails = cityWeatherDetails.getCity();
        int timeZone = cityDetails.getTimezone();

        // Extract the date from the first weather data entry (assuming the first entry represents the start of the forecast)
        String currentDate = DateTimeManager.extractDateFromUnix(cityWeatherDetails.getList().getFirst().getDt(), timeZone);

        // Iterate through each weather detail entry to generate the forecast
        for (WeatherDetails weatherDetails : cityWeatherDetails.getList()) {

            // Instantiate a new WeatherPrediction object for each weather detail
            WeatherPrediction prediction = new WeatherPrediction();

            // Get the date and time for this specific weather entry based on the time zone
            String cityDate = DateTimeManager.extractDateFromUnix(weatherDetails.getDt(), timeZone);
            String cityTime = DateTimeManager.extractTimeFromUnix(weatherDetails.getDt(), timeZone);

            // Set the time of this weather prediction
            prediction.setTime(cityTime);

            // Convert the temperature from Kelvin to Celsius and round to the nearest integer
            double tempCelsius = Math.round(weatherDetails.getMain().getTemp() - 273.15);
            prediction.setTemperature(tempCelsius);

            // Create a list to store weather status (e.g., description and main weather type)
            List<WeatherStatus> statusList = new ArrayList<>();
            StringBuilder advice = new StringBuilder();

            // Iterate through the weather conditions and build the advice and status list
            weatherDetails.getWeather().forEach(weather -> {
                // Create a WeatherStatus object for each condition and add to the status list
                WeatherStatus weatherStatus = new WeatherStatus();
                weatherStatus.setDescription(weather.getDescription());
                weatherStatus.setStatus(weather.getMain());
                statusList.add(weatherStatus);

                // Append the weather-specific advice to the advice builder
                advice.append(WeatherAdviceManager.getWeatherAdvice(weather));
            });

            // Set the weather status list to the prediction
            prediction.setWeather(statusList);

            // Get the wind details for this weather entry
            Wind wind = weatherDetails.getWind();
            double windSpeed = wind.getSpeed();

            // Advice based on temperature (if above 40°C)
            if (tempCelsius > 40) {
                advice.append("Use sunscreen lotion. ");
            }

            // Advice based on wind speed (if above 10 m/s)
            if (windSpeed > 10) {
                advice.append("It’s too windy, watch out! ");
            }

            // Set the final advice for this prediction (or a default message if no advice)
            prediction.setAdvice(!advice.isEmpty() ? advice.toString() : "No advice as of now!!");

            // Check if the date has changed to store weather predictions for a new day
            if (!currentDate.equals(cityDate)) {
                datedList = new ArrayList<>();  // Clear the dated list for the new day
                currentDate = cityDate; // Update the current date
            }

            // Add the prediction to the list for the current date
            datedList.add(prediction);

            // Store the list of predictions for this date in the map
            map.put(cityDate, datedList);
        }

        // Set the success message, status code, and data in the final city weather prediction object
        cityWeatherPrediction.setMessage("success");
        cityWeatherPrediction.setData(map);
        cityWeatherPrediction.setStatus(200);

        // Return the generated weather advice for the city
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

