CREATE TABLE restaurants (
    id                      BIGSERIAL PRIMARY KEY,
    name                    VARCHAR(200) NOT NULL,
    latitude                DOUBLE PRECISION NOT NULL,
    longitude               DOUBLE PRECISION NOT NULL,
    zone_id                 BIGINT REFERENCES zones(id),
    avg_prep_time_seconds   INT NOT NULL DEFAULT 600,
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_restaurants_zone ON restaurants(zone_id);
