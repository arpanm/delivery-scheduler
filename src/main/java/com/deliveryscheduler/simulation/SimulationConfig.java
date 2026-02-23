package com.deliveryscheduler.simulation;

/**
 * Configuration for a simulation run.
 */
public class SimulationConfig {

    private int numberOfZones = 20;
    private int numberOfRestaurants = 500;
    private int numberOfRiders = 100;
    private double ordersPerMinute = 5.0;
    private double peakMultiplier = 2.5;
    private int simulationDurationMinutes = 120;
    private double timeAccelerationFactor = 10.0;

    // City bounds (default: ~10km x 10km grid centered on origin)
    private double cityMinLat = 12.90;
    private double cityMaxLat = 13.00;
    private double cityMinLon = 77.55;
    private double cityMaxLon = 77.65;

    // Delivery promise parameters
    private int minDeliveryPromiseMinutes = 30;
    private int maxDeliveryPromiseMinutes = 60;

    public int getNumberOfZones() { return numberOfZones; }
    public void setNumberOfZones(int numberOfZones) { this.numberOfZones = numberOfZones; }
    public int getNumberOfRestaurants() { return numberOfRestaurants; }
    public void setNumberOfRestaurants(int numberOfRestaurants) { this.numberOfRestaurants = numberOfRestaurants; }
    public int getNumberOfRiders() { return numberOfRiders; }
    public void setNumberOfRiders(int numberOfRiders) { this.numberOfRiders = numberOfRiders; }
    public double getOrdersPerMinute() { return ordersPerMinute; }
    public void setOrdersPerMinute(double ordersPerMinute) { this.ordersPerMinute = ordersPerMinute; }
    public double getPeakMultiplier() { return peakMultiplier; }
    public void setPeakMultiplier(double peakMultiplier) { this.peakMultiplier = peakMultiplier; }
    public int getSimulationDurationMinutes() { return simulationDurationMinutes; }
    public void setSimulationDurationMinutes(int simulationDurationMinutes) { this.simulationDurationMinutes = simulationDurationMinutes; }
    public double getTimeAccelerationFactor() { return timeAccelerationFactor; }
    public void setTimeAccelerationFactor(double timeAccelerationFactor) { this.timeAccelerationFactor = timeAccelerationFactor; }
    public double getCityMinLat() { return cityMinLat; }
    public double getCityMaxLat() { return cityMaxLat; }
    public double getCityMinLon() { return cityMinLon; }
    public double getCityMaxLon() { return cityMaxLon; }
    public int getMinDeliveryPromiseMinutes() { return minDeliveryPromiseMinutes; }
    public int getMaxDeliveryPromiseMinutes() { return maxDeliveryPromiseMinutes; }
}
