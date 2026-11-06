-- What the vehicle carries besides the driver: passengers, luggage, cargo. It counts in the fuel.
-- Nought for a vehicle saved before it was asked. The range is the API's as well, so a row that
-- got past the API some other way is refused all the same.
ALTER TABLE vehicle
    ADD COLUMN load_kilograms integer NOT NULL DEFAULT 0 CHECK (load_kilograms BETWEEN 0 AND 1500);
