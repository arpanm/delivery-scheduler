package com.deliveryscheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "scheduler")
public class SchedulerProperties {

    private Insertion insertion = new Insertion();
    private Optimization optimization = new Optimization();
    private Cost cost = new Cost();

    public Insertion getInsertion() { return insertion; }
    public void setInsertion(Insertion insertion) { this.insertion = insertion; }
    public Optimization getOptimization() { return optimization; }
    public void setOptimization(Optimization optimization) { this.optimization = optimization; }
    public Cost getCost() { return cost; }
    public void setCost(Cost cost) { this.cost = cost; }

    public static class Insertion {
        private int maxCandidateRiders = 20;
        private double maxRadiusMeters = 5000;

        public int getMaxCandidateRiders() { return maxCandidateRiders; }
        public void setMaxCandidateRiders(int maxCandidateRiders) { this.maxCandidateRiders = maxCandidateRiders; }
        public double getMaxRadiusMeters() { return maxRadiusMeters; }
        public void setMaxRadiusMeters(double maxRadiusMeters) { this.maxRadiusMeters = maxRadiusMeters; }
    }

    public static class Optimization {
        private long intervalMs = 30000;
        private int maxIterations = 500;
        private long maxTimeMs = 5000;

        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
        public int getMaxIterations() { return maxIterations; }
        public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
        public long getMaxTimeMs() { return maxTimeMs; }
        public void setMaxTimeMs(long maxTimeMs) { this.maxTimeMs = maxTimeMs; }
    }

    public static class Cost {
        private double weightDistance = 1.0;
        private double weightTime = 0.5;
        private double weightDetour = 2.0;
        private double weightIdle = 0.3;

        public double getWeightDistance() { return weightDistance; }
        public void setWeightDistance(double weightDistance) { this.weightDistance = weightDistance; }
        public double getWeightTime() { return weightTime; }
        public void setWeightTime(double weightTime) { this.weightTime = weightTime; }
        public double getWeightDetour() { return weightDetour; }
        public void setWeightDetour(double weightDetour) { this.weightDetour = weightDetour; }
        public double getWeightIdle() { return weightIdle; }
        public void setWeightIdle(double weightIdle) { this.weightIdle = weightIdle; }
    }
}
