package com.ps.weatherapp.models;

import java.util.List;
import java.util.Map;

public class CityWeatherPrediction {

    private String message;
    private int status;
    private Map<String, List<WeatherPrediction>> data;

    public CityWeatherPrediction(String errorMessage, Map<String, List<WeatherPrediction>> data, int status) {
        this.message = errorMessage;
        this.data = data;
        this.status = status;
    }

    public CityWeatherPrediction() {
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String errorMessage) {
        this.message = errorMessage;
    }

    public Map<String, List<WeatherPrediction>> getData() {
        return data;
    }

    public void setData(Map<String, List<WeatherPrediction>> data) {
        this.data = data;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }
}
