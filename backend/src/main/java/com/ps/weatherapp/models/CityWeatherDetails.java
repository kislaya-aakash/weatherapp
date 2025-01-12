package com.ps.weatherapp.models;

import java.util.List;

public class CityWeatherDetails {
    private String cod;
    private int cnt;
    private String message;
    private List<WeatherDetails> list;
    private City city;

    public String getCod() {
        return cod;
    }

    public void setCod(String cod) {
        this.cod = cod;
    }

    public int getCnt() {
        return cnt;
    }

    public void setCnt(int cnt) {
        this.cnt = cnt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<WeatherDetails> getList() {
        return list;
    }

    public void setList(List<WeatherDetails> list) {
        this.list = list;
    }

    public City getCity() {
        return city;
    }

    public void setCity(City city) {
        this.city = city;
    }

    public CityWeatherDetails(String cod, int cnt, String message, List<WeatherDetails> list,City city) {
        this.cod = cod;
        this.cnt = cnt;
        this.message = message;
        this.list = list;
        this.city = city;
    }

    public CityWeatherDetails() {
    }

    public CityWeatherDetails(String message, List<WeatherDetails> list, String cod) {
        this.message = message;
        this.list = list;
        this.cod = cod;
    }

    public CityWeatherDetails(String message, List<WeatherDetails> list) {
        this.message = message;
        this.list = list;

    }
}
