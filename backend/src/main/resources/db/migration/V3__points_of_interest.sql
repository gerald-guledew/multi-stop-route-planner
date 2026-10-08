-- Named places: shops, schools, hospitals, anything people call by its name rather than by
-- its street address. The address table cannot answer "New World Remuera", because LINZ
-- records where addresses are, not what stands on them.
--
-- Filled from Overture Maps places. The source column lets a second dataset sit beside it
-- later, and keeps rows that arrived under different licences apart.

CREATE TABLE poi (
    -- The source's own id rather than a generated one, so importing a newer release updates
    -- rows instead of duplicating them. Overture's are UUIDs. Kept as text because another
    -- source may use something else.
    id          text PRIMARY KEY,
    source      text NOT NULL,
    name        text NOT NULL,
    -- As the source wrote it, for example "10 Clonbern Rd". Not a LINZ address.
    address     text,
    locality    text,
    category    text,
    -- How sure the source is that the place exists, from 0 to 1.
    confidence  real NOT NULL,
    -- Copied from the nearest LINZ address after loading. Sources usually give only the
    -- city, and people search by suburb: "the warehouse newmarket".
    suburb      text,
    latitude    double precision NOT NULL,
    longitude   double precision NOT NULL,
    -- What a search is compared against. Generated, so filling in the suburb later brings
    -- it up to date without anything having to remember to.
    search_text text GENERATED ALWAYS AS (
                    searchable(
                        name || ' ' || coalesce(address, '') || ' ' || coalesce(suburb, '')
                        || ' ' || coalesce(locality, ''))
                ) STORED,
    location    geography(Point, 4326)
                GENERATED ALWAYS AS (
                    ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
                ) STORED
);

CREATE INDEX poi_search_text_trgm ON poi USING gin (search_text gin_trgm_ops);
