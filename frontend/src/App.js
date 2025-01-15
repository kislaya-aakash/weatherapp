import React, { useState } from "react";
import "./App.css";
import Header from "./components/Header.jsx";
import Footer from "./components/Footer.jsx";
import SearchBar from "./components/SearchBar.jsx";
import WeatherTable from "./components/WeatherTable.jsx";
import "bootstrap/dist/css/bootstrap.min.css";

function App() {
  const [weatherData, setWeatherData] = useState(null);

  return (
    <div>
      {/* Header */}
      <Header />

      {/* Main Content */}
      <div
        id="top"
        style={{ marginTop: "56px", minHeight: "100vh", paddingTop: "20px" }}
      >
        {/* Search Bar */}
        <SearchBar setWeatherData={setWeatherData} />

        {/* Display weather details or message */}
        <div className="mt-5 text-center">
          {weatherData ? (
            // Pass weatherData to WeatherTable component for rendering
            <WeatherTable weatherData={weatherData} />
          ) : (
            <p>Enter a city to get weather details.</p>
          )}
        </div>
      </div>

      {/* Footer */}
      <Footer />
    </div>
  );
}

export default App;
