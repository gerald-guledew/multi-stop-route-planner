-- What kind of place each one is, for finding a stop by what it is when there is no name to type:
-- a fuel station, a beach, a dairy. One of the names in PlaceKind, and null for a place of no kind
-- that is looked for that way.
--
-- Filled by the import, from OpenStreetMap and Overture together. It also adds, with the source
-- 'osm', the places only OpenStreetMap has.

ALTER TABLE poi ADD COLUMN place_kind text;

-- For the nearest places of a kind to a road. Most places have no kind.
CREATE INDEX poi_place_kind_location ON poi USING gist (location) WHERE place_kind IS NOT NULL;
