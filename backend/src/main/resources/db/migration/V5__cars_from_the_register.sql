-- Petrol cars as NZTA's Motor Vehicle Register describes them, so a driver can find theirs by
-- make, model, year and engine size instead of typing its figures. One row for each make,
-- model, year, engine size and origin, with the figures most of those cars carry. Loaded from
-- the file scripts/export-nzta-cars.sh writes, and replaced whole by each import.
--
-- litres_per_100_km is the combined figure of the laboratory test the car was approved with.
-- Real driving uses more. A used import keeps the figure of the country it came from, which
-- usually reads lower than the same car sold new here, so the two are told apart. A driver is
-- not asked which theirs is: a car goes by the figures of whichever of the two has more cars.
--
-- The urban and extra-urban figures, the gross mass and the seats are kept for working out
-- speed and hills. Most used imports have no urban figure. The gross mass of a used import
-- is the car and 55 kg a seat, where a car sold new here has the most its maker allows loaded.
CREATE TABLE car_variant (
    make                          text NOT NULL,
    model                         text NOT NULL,
    year                          smallint NOT NULL,
    engine_litres                 numeric(3, 1) NOT NULL,
    imported_used                 boolean NOT NULL,
    cars                          integer NOT NULL CHECK (cars > 0),
    litres_per_100_km             double precision NOT NULL CHECK (litres_per_100_km BETWEEN 2 AND 30),
    urban_litres_per_100_km       double precision,
    extra_urban_litres_per_100_km double precision,
    gross_mass_kg                 integer,
    seats                         smallint,
    PRIMARY KEY (make, model, year, engine_litres, imported_used)
);

-- A vehicle is now entered by hand, with its size, or found in that table, with its make,
-- model, year and engine size. A car found there goes by the register's figure, unless the
-- driver also typed what the dashboard shows.
ALTER TABLE vehicle
    ALTER COLUMN size DROP NOT NULL,
    ADD COLUMN make text,
    ADD COLUMN model text,
    ADD COLUMN year smallint,
    ADD COLUMN engine_litres numeric(3, 1),
    ADD COLUMN figure_from text NOT NULL DEFAULT 'DASHBOARD' CHECK (figure_from IN ('DASHBOARD', 'REGISTER')),
    ADD CONSTRAINT vehicle_entered_or_found CHECK (
        (size IS NOT NULL AND make IS NULL AND model IS NULL AND year IS NULL
            AND engine_litres IS NULL AND figure_from = 'DASHBOARD')
        OR (size IS NULL AND make IS NOT NULL AND model IS NOT NULL AND year IS NOT NULL
            AND engine_litres IS NOT NULL));
