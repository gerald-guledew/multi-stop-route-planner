-- Suburbs, towns and the other areas people call a place by, so that a search for "remuera" finds
-- Remuera itself, and not only what is in it. LINZ lists the addresses in a suburb and Overture
-- the businesses, and neither the suburb.
--
-- Filled by the places import from the OpenStreetMap export, scripts/export-osm-areas.sql.

CREATE TABLE area (
    -- The map's own: "node/123", "way/456" or "relation/789".
    id          text PRIMARY KEY,
    name        text NOT NULL,
    -- The city of the nearest LINZ address, as LINZ writes it, which tells apart two areas of
    -- one name: Hillcrest in Auckland and Hillcrest in Hamilton. Null where no address is near,
    -- and where the city is the area itself.
    city        text,
    latitude    double precision NOT NULL,
    longitude   double precision NOT NULL,
    -- What a search is compared against, as for the other two tables.
    search_text text GENERATED ALWAYS AS (searchable(name || ' ' || coalesce(city, ''))) STORED,
    location    geography(Point, 4326)
                GENERATED ALWAYS AS (
                    ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
                ) STORED
);

CREATE INDEX area_search_text_trgm ON area USING gin (search_text gin_trgm_ops);
