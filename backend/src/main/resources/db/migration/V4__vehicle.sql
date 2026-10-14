-- The vehicle the planner works out fuel for. One driver, one vehicle, so the table holds one
-- row at most, and its id says so. Petrol cars only for now, so no fuel is stored.
--
-- litres_per_100_km is what the dashboard shows. size stands in for the weight, which few
-- drivers know. The ranges here are the API's as well, so a row that got past the API some
-- other way is refused all the same.
CREATE TABLE vehicle (
    id                smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    litres_per_100_km double precision NOT NULL CHECK (litres_per_100_km BETWEEN 2 AND 30),
    size              text NOT NULL CHECK (size IN ('SMALL', 'MEDIUM', 'LARGE'))
);
