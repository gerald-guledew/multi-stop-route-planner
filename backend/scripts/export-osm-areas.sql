-- Writes the suburbs, towns and other named areas to a CSV, from the OpenStreetMap extract the
-- roads are built from. The API loads that file with --routeplanner.poi.import-enabled=true, so
-- that a search for a suburb finds the suburb itself, and not only what is in it.
--
-- Run it from the backend directory:
--
--     duckdb -f scripts/export-osm-areas.sql
--
-- Needs DuckDB 1.1 or later, and reads only the extract already on disk. Takes a few seconds.
--
-- An area is mostly a single point, put where the area is known to be. Some are drawn as an
-- outline too, or only as one, and an outline is stood at the average of its points. An outline
-- made of several pieces is a relation, and takes the points of them all.
--
-- The areas kept are the ones people live in or call a place by: a city, a town, a village, a
-- suburb, a quarter or a neighbourhood of one, a hamlet, and a locality, which is a named place
-- where nobody is counted as living. Islands are left out, as most have no road.

INSTALL spatial;
LOAD spatial;

CREATE TEMP TABLE wanted AS
SELECT kind AS element,
       id,
       tags['place'] AS place,
       tags['name'] AS name,
       lat,
       lon,
       refs,
       ref_types
FROM ST_ReadOSM('data/new-zealand-latest.osm.pbf')
WHERE tags IS NOT NULL
  AND tags['place'] IN ('city', 'town', 'village', 'suburb', 'quarter', 'neighbourhood', 'hamlet', 'locality')
  AND tags['name'] IS NOT NULL;

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
           wanted.place,
           round(coalesce(wanted.lat, centre.lat), 7) AS latitude,
           round(coalesce(wanted.lon, centre.lon), 7) AS longitude
    FROM wanted
    LEFT JOIN centre ON centre.element = wanted.element AND centre.id = wanted.id
    WHERE coalesce(wanted.lat, centre.lat) IS NOT NULL
    -- Sorted, so two exports of the same extract are the same file.
    ORDER BY id
) TO 'data/osm-nz-areas.csv' (FORMAT csv, HEADER true);

SELECT place, count(*) AS areas
FROM read_csv('data/osm-nz-areas.csv')
GROUP BY place
ORDER BY areas DESC;
