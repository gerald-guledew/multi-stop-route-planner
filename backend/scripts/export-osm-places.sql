-- Writes the places people stop at on a drive to a CSV, each with the kind of place it is, from
-- the OpenStreetMap extract the roads are built from. The API loads that file with
-- --routeplanner.poi.import-enabled=true, and matches it with the Overture places.
--
-- Run it from the backend directory:
--
--     duckdb -f scripts/export-osm-places.sql
--
-- Needs DuckDB 1.1 or later, and reads only the extract already on disk. Takes a few seconds.
--
-- A place on the map is a single point, or an outline such as a beach or a mall. An outline is
-- stood at the average of its points, which is near enough to drive to: the engine goes to the
-- nearest road. An outline made of several pieces is a relation, and takes the points of them all.

INSTALL spatial;
LOAD spatial;

CREATE TEMP TABLE wanted AS
SELECT kind AS element,
       id,
       CASE
           WHEN tags['amenity'] = 'fuel' THEN 'fuel'
           WHEN tags['shop'] = 'convenience' THEN 'dairy'
           WHEN tags['shop'] IN ('supermarket', 'greengrocer') THEN 'groceries'
           WHEN tags['amenity'] = 'cafe' THEN 'cafe'
           WHEN tags['amenity'] IN ('restaurant', 'fast_food') THEN 'food'
           WHEN tags['amenity'] = 'toilets' THEN 'toilets'
           WHEN tags['leisure'] = 'park' THEN 'park'
           WHEN tags['natural'] = 'beach' THEN 'beach'
           WHEN tags['tourism'] = 'viewpoint' THEN 'lookout'
           WHEN tags['tourism'] = 'zoo' THEN 'zoo'
           WHEN tags['tourism'] IN ('attraction', 'museum', 'theme_park', 'aquarium') THEN 'tourist'
           WHEN tags['shop'] = 'mall' THEN 'mall'
       END AS place_kind,
       -- Toilets and lookouts are worth stopping at with no name, and are nearly all without one.
       -- A toilet block is often named for the beach or park it is at, which is no name for it.
       CASE WHEN tags['amenity'] = 'toilets' AND lower(coalesce(tags['name'], '')) NOT LIKE '%toilet%'
            THEN 'Public toilets'
            ELSE coalesce(tags['name'], tags['brand'],
                          CASE WHEN tags['tourism'] = 'viewpoint' THEN 'Lookout' END)
       END AS name,
       lat,
       lon,
       refs,
       ref_types
FROM ST_ReadOSM('data/new-zealand-latest.osm.pbf')
WHERE tags IS NOT NULL
  AND place_kind IS NOT NULL
  AND name IS NOT NULL
  -- Nobody can stop there.
  AND coalesce(tags['access'], '') NOT IN ('private', 'no', 'customers');

CREATE TEMP TABLE piece AS
SELECT id AS relation, unnest(refs) AS way, unnest(ref_types) AS element
FROM wanted
WHERE element = 'relation';

CREATE TEMP TABLE way_of_a_relation AS
SELECT piece.relation, osm.refs
FROM ST_ReadOSM('data/new-zealand-latest.osm.pbf') osm
JOIN piece ON piece.way = osm.id AND piece.element = 'way'
WHERE osm.kind = 'way';

CREATE TEMP TABLE outline_point AS
SELECT 'way' AS element, id, unnest(refs) AS node FROM wanted WHERE element = 'way'
UNION ALL
SELECT 'relation', relation, unnest(refs) FROM way_of_a_relation;

CREATE TEMP TABLE centre AS
SELECT outline_point.element, outline_point.id, avg(osm.lat) AS lat, avg(osm.lon) AS lon
FROM ST_ReadOSM('data/new-zealand-latest.osm.pbf') osm
JOIN outline_point ON outline_point.node = osm.id
WHERE osm.kind = 'node'
GROUP BY outline_point.element, outline_point.id;

COPY (
    SELECT wanted.element || '/' || wanted.id AS id,
           wanted.name,
           wanted.place_kind,
           round(coalesce(wanted.lat, centre.lat), 7) AS latitude,
           round(coalesce(wanted.lon, centre.lon), 7) AS longitude
    FROM wanted
    LEFT JOIN centre ON centre.element = wanted.element AND centre.id = wanted.id
    WHERE coalesce(wanted.lat, centre.lat) IS NOT NULL
    -- Sorted, so two exports of the same extract are the same file.
    ORDER BY id
) TO 'data/osm-nz-places.csv' (FORMAT csv, HEADER true);

SELECT place_kind, count(*) AS places
FROM read_csv('data/osm-nz-places.csv')
GROUP BY place_kind
ORDER BY place_kind;
