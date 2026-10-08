package com.solvelify.routeplanner.search;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Searches LINZ addresses and Overture places together, in PostgreSQL.
 *
 * <p>Plain SQL through {@link JdbcClient} rather than a JPA repository, because nothing here
 * is an entity being loaded or saved. It is one read across two tables that share no class,
 * and the parts that make it work, the trigram index and {@code similarity}, belong to
 * PostgreSQL.
 */
@Service
public class PostgresPlaceSearch implements PlaceSearch {

    /**
     * One statement for both tables, so there is a single ranking rather than two lists to
     * merge.
     *
     * <p>Finding and ranking use different text on purpose. A place is found if the words
     * appear anywhere in its {@code search_text}: name, address, suburb or city. It is then
     * ranked by how close the words are to what a person would call it. For an address that
     * is the address. For a business it is the name, with or without its suburb, and not the
     * street it happens to be on. Without that split "mcdonalds" returns houses on McDonalds
     * Road ahead of the restaurants, and "277 broadway" returns the shops at that address
     * ahead of the address.
     *
     * <p>Both sides of every comparison go through {@code searchable}, which drops accents
     * and apostrophes, so "paknsave mangere" finds PAK'nSAVE in Māngere.
     *
     * <p>Each table hands over at most {@code :candidates} matches to be ranked. A short,
     * common search such as "road" matches a third of all addresses, and scoring every one
     * of them took over four seconds. Capped, the same search takes 70 milliseconds. A
     * search specific enough to be useful matches far fewer rows than the cap, so it is
     * still ranked in full.
     */
    private static final String SEARCH = """
            WITH found AS (
                (SELECT 'ADDRESS' AS kind, full_address AS name,
                        NULL AS address, NULL AS suburb, NULL AS locality,
                        latitude, longitude, 1.0 AS confidence,
                        similarity(search_text, searchable(:words)) AS score
                 FROM (SELECT full_address, search_text, latitude, longitude
                       FROM address
                       WHERE search_text ILIKE searchable(:pattern)
                       LIMIT :candidates) AS candidate
                 ORDER BY score DESC, length(full_address)
                 LIMIT :limit)
                UNION ALL
                (SELECT 'POI', name, address, suburb, locality,
                        latitude, longitude, confidence,
                        greatest(
                            similarity(searchable(name), searchable(:words)),
                            similarity(searchable(name || ' ' || coalesce(suburb, '')),
                                       searchable(:words))) AS score
                 FROM (SELECT name, address, suburb, locality, latitude, longitude, confidence
                       FROM poi
                       WHERE search_text ILIKE searchable(:pattern)
                         AND confidence >= :minConfidence
                       LIMIT :candidates) AS candidate
                 ORDER BY score DESC, confidence DESC, length(name)
                 LIMIT :limit)
            )
            SELECT kind, name, address, suburb, locality, latitude, longitude
            FROM found
            ORDER BY score DESC, confidence DESC, length(name), name
            LIMIT :limit
            """;

    private final JdbcClient jdbc;
    private final SearchProperties properties;

    public PostgresPlaceSearch(JdbcClient jdbc, SearchProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Override
    public List<FoundPlace> search(String typed, int limit) {
        Optional<SearchTerm> term = SearchTerm.from(typed);
        if (term.isEmpty()) {
            return List.of();
        }

        return jdbc.sql(SEARCH)
                .param("words", term.get().words())
                .param("pattern", term.get().pattern())
                .param("minConfidence", properties.minConfidence())
                .param("candidates", properties.candidateLimit())
                .param("limit", limit)
                .query(PostgresPlaceSearch::toFoundPlace)
                .list();
    }

    private static FoundPlace toFoundPlace(ResultSet row, int rowNumber) throws SQLException {
        String name = row.getString("name");
        double latitude = row.getDouble("latitude");
        double longitude = row.getDouble("longitude");

        return switch (FoundPlace.Kind.valueOf(row.getString("kind"))) {
            case ADDRESS -> FoundPlace.address(name, latitude, longitude);
            case POI -> FoundPlace.poi(name, row.getString("address"), row.getString("suburb"),
                    row.getString("locality"), latitude, longitude);
        };
    }
}
