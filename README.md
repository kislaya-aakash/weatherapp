# CityWeatherService: Weather Advisory and Backup Management

## Overview

The **WeatherApp** application provides real-time weather advisory services to users, along with a robust fallback mechanism for offline or error scenarios. The service fetches weather data from an external API and maintains a local backup for uninterrupted service during API downtimes.

### Key Features:
1. **Real-Time Weather Advisory**: Fetches weather data for cities using an external API.
2. **Offline Fallback**: Uses backup data when the service is offline or when the external API fails.
3. **Weather Advice Generation**: Provides actionable advice based on weather conditions.
4. **Data Backup and Maintenance**: Updates the local backup with the latest weather data to ensure availability.

## Workflow and Design

### **Online Mode:**

1. **Fetch Weather Data from External API**:
    - Fetches weather data for a city from the external API.
    - If the API returns valid data:
        - Generate and return weather advice.
        - If the city weather details don't exist in the backup, update the backup with the fetched data and append a `lastUpdated` field with the current UTC time.
        - If the city does exist in the backup:
            - **Update Backup Conditions**:
                - If the difference between the current UTC time and the `lastUpdated` field is greater than 30 minutes, or if the fetched weather data differs from the first entry in the backup, update the backup with the new data.
                - Set the `lastUpdated` field to the current UTC time before performing the update.

2. **API Failure Scenarios**:
    - **Wrong City Input**: If the input city is invalid, return an empty response with a corresponding error message and status code.
    - **Other Issues**: In case of other issues (e.g., API downtime), fall back to the backup and generate weather advice using the backup data. If no backup data is available, return a `503 Service Unavailable` response with the message "Service temporarily unavailable."

### **Offline Mode:**

1. **Retrieve from Backup**:
    - In offline mode, the service will directly retrieve weather details from the backup and generate weather advice based on the available backup data.
    - The weather advice is then returned to the user.

---

## Flow Diagram
