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

class PrepTimePredictionServiceTest {

    @Test
    void trainAndPredict_shouldReturnReasonableEstimate() {
        List<PrepTimeRecord> history = generateHistory(100);

        PrepTimeModel model = PrepTimeModel.train(history);

        // Predict for 3 items during off-peak
        Instant offPeak = LocalDateTime.of(2024, 1, 15, 15, 0)
                .atZone(ZoneId.systemDefault()).toInstant();
        int prediction = model.predict(3, offPeak);

        // Should be positive and reasonable (between 2 and 30 minutes)
        assertTrue(prediction >= 120, "Prediction should be at least 2 minutes: " + prediction);
        assertTrue(prediction <= 1800, "Prediction should be at most 30 minutes: " + prediction);
    }

    @Test
    void moreItems_shouldTakeLonger() {
        List<PrepTimeRecord> history = generateHistory(100);
        PrepTimeModel model = PrepTimeModel.train(history);

        Instant time = Instant.now();
        int oneItem = model.predict(1, time);
        int fiveItems = model.predict(5, time);

        assertTrue(fiveItems >= oneItem,
                "5 items should take at least as long as 1 item: " + fiveItems + " vs " + oneItem);
    }

    @Test
    void peakHour_shouldPredictLonger() {
        List<PrepTimeRecord> history = generateHistoryWithPeakEffect(200);
        PrepTimeModel model = PrepTimeModel.train(history);

        // Peak: 12:30 PM
        Instant peak = LocalDateTime.of(2024, 1, 15, 12, 30)
                .atZone(ZoneId.systemDefault()).toInstant();
        // Off-peak: 3:00 PM
        Instant offPeak = LocalDateTime.of(2024, 1, 15, 15, 0)
                .atZone(ZoneId.systemDefault()).toInstant();

        int peakPrediction = model.predict(3, peak);
        int offPeakPrediction = model.predict(3, offPeak);

        assertTrue(peakPrediction >= offPeakPrediction,
                "Peak should be >= off-peak: " + peakPrediction + " vs " + offPeakPrediction);
    }

    @Test
    void minimumPrediction_shouldBe120Seconds() {
        // Create history with very fast prep times
        List<PrepTimeRecord> history = new ArrayList<>();
        Instant time = Instant.now();
        for (int i = 0; i < 50; i++) {
            PrepTimeRecord record = new PrepTimeRecord(1L, (long) i, 1, time, 60);
            record.setActualPrepTimeSeconds(30); // very fast
            history.add(record);
        }

        PrepTimeModel model = PrepTimeModel.train(history);
        int prediction = model.predict(1, time);

        assertTrue(prediction >= 120, "Minimum prediction should be 120 seconds: " + prediction);
    }

    private List<PrepTimeRecord> generateHistory(int count) {
        List<PrepTimeRecord> history = new ArrayList<>();
        Random random = new Random(42);
        Instant base = Instant.now();

        for (int i = 0; i < count; i++) {
            int items = 1 + random.nextInt(5);
            int actual = 300 + items * 60 + random.nextInt(120); // base 5 min + 1 min per item + noise
            PrepTimeRecord record = new PrepTimeRecord(1L, (long) i, items,
                    base.minusSeconds(i * 3600L), actual);
            record.setActualPrepTimeSeconds(actual);
            history.add(record);
        }
        return history;
    }

    private List<PrepTimeRecord> generateHistoryWithPeakEffect(int count) {
        List<PrepTimeRecord> history = new ArrayList<>();
        Random random = new Random(42);

        for (int i = 0; i < count; i++) {
            int items = 1 + random.nextInt(5);
            int hour = 8 + (i % 14); // hours 8-21
            Instant orderTime = LocalDateTime.of(2024, 1, 15, hour, 30)
                    .atZone(ZoneId.systemDefault()).toInstant();

            int base = 300 + items * 60;
            // Peak hours get 50% increase
            boolean isPeak = (hour >= 12 && hour < 13) || (hour >= 19 && hour < 21);
            int actual = isPeak ? (int) (base * 1.5) : base;
            actual += random.nextInt(60);

            PrepTimeRecord record = new PrepTimeRecord(1L, (long) i, items, orderTime, actual);
            record.setActualPrepTimeSeconds(actual);
            history.add(record);
        }
        return history;
    }
}
