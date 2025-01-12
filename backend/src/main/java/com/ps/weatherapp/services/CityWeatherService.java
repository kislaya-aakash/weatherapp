package com.ps.weatherapp.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ps.weatherapp.interfaces.ICityWeatherService;
import com.ps.weatherapp.models.*;
import com.ps.weatherapp.utilities.ExternalAPIManager;
import com.ps.weatherapp.utilities.FileManager;

import com.ps.weatherapp.utilities.DateTimeManager;
import com.ps.weatherapp.utilities.WeatherAdviceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CityWeatherService implements ICityWeatherService {

    private final ExternalAPIManager apiManager;
    private final FileManager<CityWeatherDetails> fileManager;
    private Map<String, CityWeatherDetails> weatherBackUpData;
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

    public CityWeatherService(WebClient webClient, FileManager<CityWeatherDetails> fileManager, ExternalAPIManager apiManager) {
        this.apiManager = apiManager;
        this.fileManager = fileManager;
        this.weatherBackUpData = new LinkedHashMap<>();
    }

    @Override
    public Mono<CityWeatherAdvice> getWeatherAdvice(String city) {
        weatherBackUpData = fileManager.loadDataFromFile(fileName, new TypeReference<>() {});

        if (!isOnline) {
            logger.info("Service is in offline mode. Fetching data from back up.");
            return getWeatherAdviceFromBackUp(city)
                    .switchIfEmpty(Mono.just(new CityWeatherAdvice("No data available currently for ".concat(city), new LinkedHashMap<>(), 503)));
        }

        return getCityWeatherDetails(city)
            .flatMap(cityWeatherDetails -> {
                if ("200".equals(cityWeatherDetails.getCod())) {
                    return Mono.just(generateWeatherAdvice(cityWeatherDetails));
                } else {
                    return getWeatherAdviceFromBackUp(city)
                        .switchIfEmpty(Mono.just(new CityWeatherAdvice(cityWeatherDetails.getMessage(),
                            new LinkedHashMap<>(),
                            Integer.parseInt(cityWeatherDetails.getCod()))));
                }
            })
            .onErrorResume(error -> {
                logger.error(error.getMessage());
                return getWeatherAdviceFromBackUp(city)
                    .switchIfEmpty(Mono.just(new CityWeatherAdvice("Service temporarily unavailable.", new LinkedHashMap<>(), 503)));
            });
    }

    private Mono<CityWeatherDetails> getCityWeatherDetails(String city) {
        Map<String, Object> queryParams = createQueryParams(city);

        return apiManager.fetchData(serviceType, queryParams, CityWeatherDetails.class)
            .flatMap(cityWeatherDetails -> {
                updateWeatherBackUpData(city, cityWeatherDetails);
                return Mono.just(cityWeatherDetails);
            })
            .onErrorResume(error -> {
                logger.error("Error fetching weather data for {}: {}", city, error.getMessage());
                return Mono.just(new CityWeatherDetails("Service temporarily unavailable.", Collections.emptyList(), "503"));
            });
    }

    private void updateWeatherBackUpData(String city, CityWeatherDetails cityWeatherDetails) {
        if ("200".equals(cityWeatherDetails.getCod())) {
            weatherBackUpData.put(city, cityWeatherDetails);
            fileManager.saveDataToFile(fileName, weatherBackUpData);
        }
    }

    private Map<String, Object> createQueryParams(String city) {
        return Map.of(
                "q", city,
                "cnt", count,
                "appid", apiKey
        );
    }

    //Generate weather advice based on weather conditions
    private CityWeatherAdvice generateWeatherAdvice(CityWeatherDetails cityWeatherDetails) {

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

        // Create and return the generated weather advice for the city
        return new CityWeatherAdvice("success", map, 200);
    }

    private Mono<CityWeatherAdvice> getWeatherAdviceFromBackUp(String city) {
        CityWeatherDetails cityWeatherDetails = weatherBackUpData.get(city);
        if (cityWeatherDetails != null) {
            weatherBackUpData.put(city, removePastCityWeatherDetails(cityWeatherDetails));
            fileManager.saveDataToFile(fileName, weatherBackUpData);
            return Mono.just(generateWeatherAdvice(cityWeatherDetails));
        }
        logger.warn("No data available for {} in back up.", city);
        return Mono.empty();
    }

    private static CityWeatherDetails removePastCityWeatherDetails(CityWeatherDetails cachedWeather) {
        // Get the current UTC time
        LocalDateTime currentUtcTime = LocalDateTime.now(ZoneOffset.UTC);

        // Define the formatter to parse the "dt_txt" field
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Filter the list to only include records where "dtTxt" is in the future
        // Keep only future records
        List<WeatherDetails> filteredList = cachedWeather.getList().stream()
            .filter(weather -> {
                LocalDateTime dt = LocalDateTime.parse(weather.getDtTxt(), formatter);
                return dt.isAfter(currentUtcTime);  // Keep only future records
            }).collect(Collectors.toList());
        cachedWeather.setList(filteredList);
        cachedWeather.setCnt(filteredList.size());
        return cachedWeather;
    }
}

