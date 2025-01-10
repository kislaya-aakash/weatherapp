package com.ps.weatherapp.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;


public class WeatherDetails {
    private long dt;
    private WeatherAttributes main;
    private List<WeatherInfo> weather;
    private Clouds clouds;
    private Wind wind;
    private long visibility;
    private long pop;
    private Sys sys;

    @JsonProperty("dt_txt")
    private String dtTxt;
    private Map<String, Double> snow;

    public WeatherDetails(long dt, WeatherAttributes main, List<WeatherInfo> weather, Clouds clouds, Wind wind, long visibility, long pop, Sys sys, String dtTxt, Map<String, Double> snow) {
        this.dt = dt;
        this.main = main;
        this.weather = weather;
        this.clouds = clouds;
        this.wind = wind;
        this.visibility = visibility;
        this.pop = pop;
        this.sys = sys;
        this.dtTxt = dtTxt;
        this.snow = snow;
    }

    public WeatherDetails() {}

    public long getDt() {
        return dt;
    }

    public void setDt(long dt) {
        this.dt = dt;
    }

    public WeatherAttributes getMain() {
        return main;
    }

    public void setMain(WeatherAttributes main) {
        this.main = main;
    }

    public List<WeatherInfo> getWeather() {
        return weather;
    }

    public void setWeather(List<WeatherInfo> weather) {
        this.weather = weather;
    }

    public Clouds getClouds() {
        return clouds;
    }

    public void setClouds(Clouds clouds) {
        this.clouds = clouds;
    }

    public Wind getWind() {
        return wind;
    }

    public void setWind(Wind wind) {
        this.wind = wind;
    }

    public long getVisibility() {
        return visibility;
    }

    public void setVisibility(long visibility) {
        this.visibility = visibility;
    }

    public long getPop() {
        return pop;
    }

    public void setPop(long pop) {
        this.pop = pop;
    }

    public Sys getSys() {
        return sys;
    }

    public void setSys(Sys sys) {
        this.sys = sys;
    }

    public String getDtTxt() {
        return dtTxt;
    }

    public void setDtTxt(String dtTxt) {
        this.dtTxt = dtTxt;
    }

    public Map<String, Double> getSnow() {
        return snow;
    }

    public void setSnow(Map<String, Double> snow) {
        this.snow = snow;
    }
}
