package com.deliveryscheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "scheduler")
public class SchedulerProperties {

    private Insertion insertion = new Insertion();
    private Optimization optimization = new Optimization();
    private Cost cost = new Cost();
    private Retry retry = new Retry();

    public Insertion getInsertion() { return insertion; }
    public void setInsertion(Insertion insertion) { this.insertion = insertion; }
    public Optimization getOptimization() { return optimization; }
    public void setOptimization(Optimization optimization) { this.optimization = optimization; }
    public Cost getCost() { return cost; }
    public void setCost(Cost cost) { this.cost = cost; }
    public Retry getRetry() { return retry; }
    public void setRetry(Retry retry) { this.retry = retry; }

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
        private int freezeWindowSeconds = 300;

        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
        public int getMaxIterations() { return maxIterations; }
        public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
        public long getMaxTimeMs() { return maxTimeMs; }
        public void setMaxTimeMs(long maxTimeMs) { this.maxTimeMs = maxTimeMs; }
        public int getFreezeWindowSeconds() { return freezeWindowSeconds; }
        public void setFreezeWindowSeconds(int freezeWindowSeconds) { this.freezeWindowSeconds = freezeWindowSeconds; }
    }

    public static class Cost {
        private double weightDistance = 1.0;
        private double weightTime = 0.5;
        private double weightDetour = 2.0;
        private double weightIdle = 0.3;
        private double weightSlaRisk = 3.0;
        private double weightBatchingBonus = 1.5;
        private double weightReassignmentPenalty = 0.8;
        private int slaRiskThresholdSeconds = 300;

        public double getWeightDistance() { return weightDistance; }
        public void setWeightDistance(double weightDistance) { this.weightDistance = weightDistance; }
        public double getWeightTime() { return weightTime; }
        public void setWeightTime(double weightTime) { this.weightTime = weightTime; }
        public double getWeightDetour() { return weightDetour; }
        public void setWeightDetour(double weightDetour) { this.weightDetour = weightDetour; }
        public double getWeightIdle() { return weightIdle; }
        public void setWeightIdle(double weightIdle) { this.weightIdle = weightIdle; }
        public double getWeightSlaRisk() { return weightSlaRisk; }
        public void setWeightSlaRisk(double weightSlaRisk) { this.weightSlaRisk = weightSlaRisk; }
        public double getWeightBatchingBonus() { return weightBatchingBonus; }
        public void setWeightBatchingBonus(double weightBatchingBonus) { this.weightBatchingBonus = weightBatchingBonus; }
        public double getWeightReassignmentPenalty() { return weightReassignmentPenalty; }
        public void setWeightReassignmentPenalty(double weightReassignmentPenalty) { this.weightReassignmentPenalty = weightReassignmentPenalty; }
        public int getSlaRiskThresholdSeconds() { return slaRiskThresholdSeconds; }
        public void setSlaRiskThresholdSeconds(int slaRiskThresholdSeconds) { this.slaRiskThresholdSeconds = slaRiskThresholdSeconds; }
    }

    public static class Retry {
        private int maxAttempts = 3;
        private long delayMs = 30000;

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getDelayMs() { return delayMs; }
        public void setDelayMs(long delayMs) { this.delayMs = delayMs; }
    }
}
