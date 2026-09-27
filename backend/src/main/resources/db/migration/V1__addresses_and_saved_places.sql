-- Address search and saved places.
--
-- pg_trgm is what lets "bassett rd remu" find "20A Bassett Road, Remuera" without exact
-- spelling. PostGIS is not needed for that, but it earns its place for anything spatial
-- later, such as finding the nearest address to a dropped pin.

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS postgis;

-- Every current New Zealand address, from Toitū Te Whenua LINZ, CC BY 4.0.
CREATE TABLE address (
    id           bigint PRIMARY KEY,
    full_address text NOT NULL,
    suburb       text,
    town_city    text,
    latitude     double precision NOT NULL,
    longitude    double precision NOT NULL,
    -- Derived, so nothing has to remember to keep it in step with the two columns above.
    location     geography(Point, 4326)
                 GENERATED ALWAYS AS (
                     ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
                 ) STORED
);

-- Trigram index: the one that makes a partial, misspelled search fast over millions of rows.
CREATE INDEX address_full_address_trgm ON address USING gin (full_address gin_trgm_ops);
CREATE INDEX address_location_gist ON address USING gist (location);

-- Places the user saved, which may be an address or a pin dropped somewhere with no address.
CREATE TABLE saved_place (
    id         bigserial PRIMARY KEY,
    name       text NOT NULL,
    latitude   double precision NOT NULL,
    longitude  double precision NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX saved_place_name_key ON saved_place (lower(name));
