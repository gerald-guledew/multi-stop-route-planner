-- Search that forgives how people actually type.
--
-- One address in eight has a macron in it: Whangārei, Māngere, Taupō. Typed without the
-- macron, which is how most keyboards make you type it, those addresses were not found.
-- "bank street whangarei" returned nothing. "bank street whangārei" returned 101 addresses.
--
-- The fix is to compare a simplified copy of the address with a simplified copy of what was
-- typed: no accents, and no apostrophes, so "oneill street" also finds O'Neill Street.

CREATE EXTENSION IF NOT EXISTS unaccent;

-- unaccent() is marked STABLE rather than IMMUTABLE, because its dictionary could in theory
-- be edited, and PostgreSQL accepts only IMMUTABLE functions in a generated column or an
-- index. Naming the dictionary and wrapping the call is the usual way to make that promise.
CREATE FUNCTION searchable(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    RETURN unaccent('unaccent', translate($1, '''’`', ''));

-- Rewrites the table, so on the full 2.4 million addresses this migration takes about half
-- a minute. It runs once.
ALTER TABLE address
    ADD COLUMN search_text text GENERATED ALWAYS AS (searchable(full_address)) STORED;

CREATE INDEX address_search_text_trgm ON address USING gin (search_text gin_trgm_ops);

-- Nothing searches the original text any more.
DROP INDEX address_full_address_trgm;
