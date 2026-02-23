package com.deliveryscheduler.prediction;

import com.deliveryscheduler.domain.model.PrepTimeRecord;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Per-restaurant statistical model for predicting food preparation time.
 *
 * Uses linear regression on item count with peak-hour adjustment
 * and conservative blending with p90 percentile.
 */
public class PrepTimeModel {

    private double baseTime;
    private double perItemTime;
    private double peakHourMultiplier;
    private double p90PrepTime;

    private PrepTimeModel() {}

    public static PrepTimeModel train(List<PrepTimeRecord> history) {
        PrepTimeModel model = new PrepTimeModel();

        // Linear regression: prepTime = baseTime + perItemTime * itemCount
        SimpleRegression regression = new SimpleRegression();
        DescriptiveStatistics stats = new DescriptiveStatistics();

        for (PrepTimeRecord record : history) {
            if (record.getActualPrepTimeSeconds() == null) continue;
            regression.addData(record.getItemCount(), record.getActualPrepTimeSeconds());
            stats.addValue(record.getActualPrepTimeSeconds());
        }

        double intercept = regression.getIntercept();
        double slope = regression.getSlope();
        // Handle NaN from regression (e.g., zero variance in x)
        model.baseTime = Double.isNaN(intercept) ? stats.getMean() : Math.max(60, intercept);
        model.perItemTime = Double.isNaN(slope) ? 0 : Math.max(0, slope);

        // Peak hour adjustment: compare peak vs off-peak averages
        double peakSum = 0, peakCount = 0;
        double offPeakSum = 0, offPeakCount = 0;

        for (PrepTimeRecord record : history) {
            if (record.getActualPrepTimeSeconds() == null) continue;
            if (isPeakHour(record.getOrderTime())) {
                peakSum += record.getActualPrepTimeSeconds();
                peakCount++;
            } else {
                offPeakSum += record.getActualPrepTimeSeconds();
                offPeakCount++;
            }
        }

        double peakAvg = peakCount > 0 ? peakSum / peakCount : 0;
        double offPeakAvg = offPeakCount > 0 ? offPeakSum / offPeakCount : 0;
        model.peakHourMultiplier = (offPeakAvg > 0) ? peakAvg / offPeakAvg : 1.0;

        // 90th percentile for conservative buffering
        model.p90PrepTime = stats.getN() > 0 ? stats.getPercentile(90) : 600;

        return model;
    }

    /**
     * Predict prep time in seconds.
     * Blends regression prediction (70%) with p90 (30%) for a slightly
     * conservative estimate.
     */
    public int predict(int itemCount, Instant orderTime) {
        double predicted = baseTime + perItemTime * itemCount;

        if (isPeakHour(orderTime)) {
            predicted *= peakHourMultiplier;
        }

        double blended = 0.7 * predicted + 0.3 * p90PrepTime;
        return (int) Math.ceil(Math.max(blended, 120)); // minimum 2 minutes
    }

    static boolean isPeakHour(Instant time) {
        int hour = time.atZone(ZoneId.systemDefault()).getHour();
        return (hour >= 12 && hour < 13) || (hour >= 19 && hour < 21);
    }
}
