CREATE TABLE trips (
    id              BIGSERIAL PRIMARY KEY,
    rider_id        BIGINT NOT NULL REFERENCES riders(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP
);

CREATE INDEX idx_trips_rider_status ON trips(rider_id, status);

CREATE TABLE trip_stops (
    id                      BIGSERIAL PRIMARY KEY,
    trip_id                 BIGINT NOT NULL REFERENCES trips(id),
    sequence_index          INT NOT NULL,
    stop_type               VARCHAR(20) NOT NULL,
    location_latitude       DOUBLE PRECISION NOT NULL,
    location_longitude      DOUBLE PRECISION NOT NULL,
    reference_id            BIGINT NOT NULL,
    tw_earliest             TIMESTAMP NOT NULL,
    tw_latest               TIMESTAMP NOT NULL,
    estimated_arrival       TIMESTAMP,
    actual_arrival          TIMESTAMP,
    completed               BOOLEAN NOT NULL DEFAULT FALSE,
    service_time_seconds    INT NOT NULL DEFAULT 120,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trip_stops_trip ON trip_stops(trip_id, sequence_index);
