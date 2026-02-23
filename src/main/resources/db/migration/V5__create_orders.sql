CREATE TABLE orders (
    id                              BIGSERIAL PRIMARY KEY,
    restaurant_id                   BIGINT NOT NULL REFERENCES restaurants(id),
    delivery_lat                    DOUBLE PRECISION NOT NULL,
    delivery_lon                    DOUBLE PRECISION NOT NULL,
    customer_name                   VARCHAR(200) NOT NULL,
    customer_phone                  VARCHAR(20) NOT NULL,
    status                          VARCHAR(20) NOT NULL DEFAULT 'PLACED',
    placed_at                       TIMESTAMP NOT NULL DEFAULT NOW(),
    promised_delivery_by            TIMESTAMP NOT NULL,
    estimated_prep_time_seconds     INT NOT NULL,
    estimated_ready_at              TIMESTAMP NOT NULL,
    trip_id                         BIGINT REFERENCES trips(id),
    items_summary                   TEXT,
    item_count                      INT NOT NULL DEFAULT 1,
    created_at                      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_trip ON orders(trip_id);
CREATE INDEX idx_orders_restaurant ON orders(restaurant_id);
