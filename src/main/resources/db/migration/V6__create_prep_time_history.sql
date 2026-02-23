CREATE TABLE prep_time_history (
    id                              BIGSERIAL PRIMARY KEY,
    restaurant_id                   BIGINT NOT NULL REFERENCES restaurants(id),
    order_id                        BIGINT NOT NULL REFERENCES orders(id),
    item_count                      INT NOT NULL,
    order_time                      TIMESTAMP NOT NULL,
    estimated_prep_time_seconds     INT NOT NULL,
    actual_prep_time_seconds        INT,
    created_at                      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prep_history_restaurant ON prep_time_history(restaurant_id, created_at DESC);
