CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE zones (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    boundary    geometry(Polygon, 4326),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_zones_boundary ON zones USING GIST (boundary);
