-- Writes every named place in New Zealand to a CSV, from the Overture Maps places data.
-- The API loads that file with --routeplanner.poi.import-enabled=true.
--
-- Run it from the backend directory:
--
--     duckdb -f scripts/export-overture-places.sql
--
-- Needs DuckDB 1.1 or later and no account. The full dataset is about 10 GB, but this
-- downloads only a few megabytes of it: the files are Parquet, so DuckDB fetches just the
-- parts whose bounding box overlaps New Zealand.
--
-- For another country, change the bounding box and the country code below.

INSTALL httpfs;
LOAD httpfs;
INSTALL spatial;
LOAD spatial;
SET s3_region = 'us-west-2';

-- Overture publishes monthly and keeps only the last few releases online, so a release
-- name written here would stop working within weeks. Their catalog names the current one.
SET VARIABLE release = (
    SELECT latest FROM read_json('https://stac.overturemaps.org/catalog.json')
);

COPY (
    SELECT id,
           names.primary         AS name,
           addresses[1].freeform AS address,
           addresses[1].locality AS locality,
           basic_category        AS category,
           confidence,
           ST_Y(geometry)        AS latitude,
           ST_X(geometry)        AS longitude
    FROM read_parquet(
        's3://overturemaps-us-west-2/release/' || getvariable('release') || '/theme=places/type=place/*')
    WHERE bbox.xmin BETWEEN 166 AND 179
      AND bbox.ymin BETWEEN -47.5 AND -34
      -- A handful of places overseas carry coordinates inside the box by mistake.
      AND addresses[1].country = 'NZ'
      AND operating_status IS DISTINCT FROM 'permanently_closed'
    -- Sorted, so two exports of the same release are the same file.
    ORDER BY id
) TO 'data/overture-nz-places.csv' (FORMAT csv, HEADER true);

SELECT getvariable('release') AS release, count(*) AS places
FROM read_csv('data/overture-nz-places.csv');
