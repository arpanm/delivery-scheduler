CREATE TABLE riders (
    id                      BIGSERIAL PRIMARY KEY,
    name                    VARCHAR(200) NOT NULL,
    phone                   VARCHAR(20) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    zone_id                 BIGINT REFERENCES zones(id),
    last_known_lat          DOUBLE PRECISION,
    last_known_lon          DOUBLE PRECISION,
    last_location_update_at TIMESTAMP,
    max_concurrent_orders   INT NOT NULL DEFAULT 3,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_riders_zone_status ON riders(zone_id, status);
