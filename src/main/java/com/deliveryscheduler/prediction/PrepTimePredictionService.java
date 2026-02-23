package com.deliveryscheduler.prediction;

import com.deliveryscheduler.domain.model.PrepTimeRecord;
import com.deliveryscheduler.domain.repository.PrepTimeRepository;
import com.deliveryscheduler.domain.repository.RestaurantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PrepTimePredictionService {

    private static final Logger log = LoggerFactory.getLogger(PrepTimePredictionService.class);

    private final PrepTimeRepository prepTimeRepository;
    private final RestaurantRepository restaurantRepository;
    private final int minSamples;
    private final int defaultPrepTimeSeconds;

    private final ConcurrentHashMap<Long, PrepTimeModel> models = new ConcurrentHashMap<>();

    public PrepTimePredictionService(PrepTimeRepository prepTimeRepository,
                                      RestaurantRepository restaurantRepository,
                                      @Value("${prediction.min-samples-for-model:20}") int minSamples,
                                      @Value("${prediction.default-prep-time-seconds:600}") int defaultPrepTimeSeconds) {
        this.prepTimeRepository = prepTimeRepository;
        this.restaurantRepository = restaurantRepository;
        this.minSamples = minSamples;
        this.defaultPrepTimeSeconds = defaultPrepTimeSeconds;
    }

    @PostConstruct
    public void init() {
        trainModels();
    }

    @Scheduled(fixedRateString = "${prediction.model-refresh-interval-ms:3600000}")
    public void trainModels() {
        List<Long> restaurantIds = prepTimeRepository.findAllActiveRestaurantIds();
        int trained = 0;

        for (Long restaurantId : restaurantIds) {
            List<PrepTimeRecord> history = prepTimeRepository.findRecentByRestaurantId(
                    restaurantId, PageRequest.of(0, 500));

            if (history.size() < minSamples) continue;

            PrepTimeModel model = PrepTimeModel.train(history);
            models.put(restaurantId, model);
            trained++;
        }

        log.info("Trained prep time models for {} restaurants", trained);
    }

    /**
     * Predict prep time for an order.
     */
    public int predictPrepTimeSeconds(Long restaurantId, int itemCount, Instant orderTime) {
        PrepTimeModel model = models.get(restaurantId);
        if (model != null) {
            return model.predict(itemCount, orderTime);
        }

        // Fallback: use restaurant's configured average
        return restaurantRepository.findById(restaurantId)
                .map(r -> r.getAvgPrepTimeSeconds())
                .orElse(defaultPrepTimeSeconds);
    }

    /**
     * Predict prep time with variance estimation for an order.
     */
    public PredictionResult predictWithVariance(Long restaurantId, int itemCount, Instant orderTime) {
        PrepTimeModel model = models.get(restaurantId);
        if (model != null) {
            return model.predictWithVariance(itemCount, orderTime);
        }

        // Fallback: use restaurant's configured average with default variance
        int fallbackTime = restaurantRepository.findById(restaurantId)
                .map(r -> r.getAvgPrepTimeSeconds())
                .orElse(defaultPrepTimeSeconds);
        return PredictionResult.defaultPrediction(fallbackTime);
    }
}
