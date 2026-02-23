package com.deliveryscheduler.prediction;

import com.deliveryscheduler.domain.model.PrepTimeRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PredictionVarianceTest {

    @Test
    void variance_isPositiveAfterTrainingWithVariedData() {
        List<PrepTimeRecord> history = generateVariedHistory(100);
        PrepTimeModel model = PrepTimeModel.train(history);

        Instant offPeak = LocalDateTime.of(2024, 1, 15, 15, 0)
                .atZone(ZoneId.systemDefault()).toInstant();

        PredictionResult result = model.predictWithVariance(3, offPeak);

        assertTrue(result.varianceSeconds() > 0,
                "Variance should be positive after training with varied data, got: " + result.varianceSeconds());
    }

    @Test
    void predictionResult_containsMeanAndVariance() {
        List<PrepTimeRecord> history = generateVariedHistory(100);
        PrepTimeModel model = PrepTimeModel.train(history);

        Instant offPeak = LocalDateTime.of(2024, 1, 15, 15, 0)
                .atZone(ZoneId.systemDefault()).toInstant();

        PredictionResult result = model.predictWithVariance(3, offPeak);

        assertTrue(result.meanSeconds() > 0,
                "Mean should be positive, got: " + result.meanSeconds());
        assertTrue(result.varianceSeconds() > 0,
                "Variance should be positive, got: " + result.varianceSeconds());
        assertEquals("STATISTICAL", result.source(),
                "Source should be STATISTICAL for a trained model prediction");
    }

    @Test
    void fallback_returnsDefaultVariance() {
        int defaultMean = 600;
        PredictionResult result = PredictionResult.defaultPrediction(defaultMean);

        assertEquals(defaultMean, result.meanSeconds(),
                "Default prediction mean should match the provided value");
        assertEquals(defaultMean * 0.15, result.varianceSeconds(), 0.001,
                "Default prediction variance should be 15% of the mean");
        assertEquals("DEFAULT", result.source(),
                "Source should be DEFAULT for a fallback prediction");
    }

    /**
     * Generates training history with varied item counts and prep times
     * that include realistic noise/variance.
     */
    private List<PrepTimeRecord> generateVariedHistory(int count) {
        List<PrepTimeRecord> history = new ArrayList<>();
        Random random = new Random(42);
        Instant base = Instant.now();

        for (int i = 0; i < count; i++) {
            int items = 1 + random.nextInt(8);  // 1-8 items for wider spread
            // base 5 min + ~1 min per item + significant noise (up to 3 min)
            int actual = 300 + items * 60 + random.nextInt(180);
            PrepTimeRecord record = new PrepTimeRecord(1L, (long) i, items,
                    base.minusSeconds(i * 3600L), actual);
            record.setActualPrepTimeSeconds(actual);
            history.add(record);
        }
        return history;
    }
}
