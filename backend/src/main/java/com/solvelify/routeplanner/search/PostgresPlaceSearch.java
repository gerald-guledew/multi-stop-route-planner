package com.solvelify.routeplanner.search;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Searches LINZ addresses and Overture places together, in PostgreSQL.
 *
 * <p>Plain SQL through {@link JdbcClient} rather than a JPA repository, because nothing here
 * is an entity being loaded or saved. It is one read across two tables that share no class,
 * and the parts that make it work, the trigram index, {@code word_similarity} and the
 * distance between two points, belong to PostgreSQL and PostGIS.
 */
@Service
public class PostgresPlaceSearch implements PlaceSearch {

    /**
     * One statement for both tables, so there is a single ranking rather than two lists to
     * merge.
     *
     * <p><b>Finding and ranking use different text on purpose.</b> A place is found if the
     * words appear, in order, anywhere in its {@code search_text}: name, address, suburb or
     * city. It is then ranked on what a person would call it. For an address that is the
     * address. For a business it is the name, with or without its suburb, and not the street
     * it happens to be on.
     *
     * <p><b>The score is three things multiplied together.</b>
     *
     * <p>First, how well the words appear in the name. {@code word_similarity} is 1 when they
     * appear together as whole words, wherever in the name that is, so "new world" matches
     * "New World Remuera" as fully as it matches "New World". Plain {@code similarity} marks
     * the longer name down for the part nobody typed, and every branch named after its suburb
     * then loses to a branch at the other end of the country named without one. A business
     * is compared as its name followed by its suburb, which also covers the name alone:
     * any run of words in the name is a run of words in the longer text too.
     *
     * <p>The last word is usually unfinished while somebody is typing, and an unfinished word
     * loses exactly one trigram, the one that marks where it ends. {@code slack} gives that
     * trigram back, so "mcdonald" finds McDonald's as surely as it finds a firm called
     * McDonald Metals.
     *
     * <p>Second, whether an address is the one typed. An address that begins with the words
     * keeps its whole score, and one that only contains them keeps four fifths. So "12 queen
     * street" puts number 12 ahead of 112 and of 12/125, and the houses on McDonalds Road do
     * not bury the restaurants.
     *
     * <p>Third, how near the place is, when the caller said where it is looking from. Next
     * door keeps the whole score, 50 km away keeps three quarters, and nothing loses more
     * than half however far away it is. So distance decides between equal matches, and a
     * place that matches less than half as well can never overtake one that matches in full.
     * Distance counts in half kilometres. Places inside the same half kilometre are equally
     * near, which leaves the houses of one street, or the shops of one mall, to be ordered
     * by the tie-break rather than by which gate is a few metres closer.
     *
     * <p><b>Ties</b> go to the name the words account for most of, which is what plain
     * {@code similarity} measures, and then to the place the source is more sure of. Without
     * a reference point most matches tie on the score, and this is what puts them in order.
     *
     * <p><b>The work is bounded twice.</b> Each table ranks at most {@code :candidates}
     * matches, because "road" matches a third of all addresses and scoring every one of them
     * took over four seconds. With a reference point those candidates ought to be the nearest
     * ones, so up to {@code :scan} matches are measured for distance first and the nearest
     * are kept. Measuring is cheap and scoring is not. A search matching more rows than
     * {@code :scan} is ranked from the first ones found, which only happens to words too
     * common to mean anything alone.
     *
     * <p>Distance is measured on a sphere rather than on the true shape of the earth. That is
     * within half a percent, far finer than ranking needs, and under half the work.
     *
     * <p>{@code term} and {@code nearest_poi} are materialized so that what they work out is
     * worked out once: what was typed once for the query, and the simplified names once for
     * each place rather than once for each comparison that reads them.
     *
     * <p>Both sides of every comparison go through {@code searchable}, which drops accents
     * and apostrophes, so "paknsave mangere" finds PAK'nSAVE in Māngere.
     */
    private static final String SEARCH = """
            WITH term AS MATERIALIZED (
                SELECT lower(searchable(:words)) AS words,
                       1.0 / greatest(cardinality(show_trgm(searchable(:words))), 1) AS slack,
                       CAST(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) AS geography) AS origin
            ),
            nearest_address AS (
                SELECT matched.*, ST_Distance(matched.location, term.origin, false) / 1000 AS km
                FROM (SELECT full_address, search_text, latitude, longitude, location
                      FROM address
                      WHERE search_text ILIKE searchable(:pattern)
                      LIMIT :scan) AS matched, term
                ORDER BY km
                LIMIT :candidates
            ),
            nearest_poi AS MATERIALIZED (
                SELECT matched.*, ST_Distance(matched.location, term.origin, false) / 1000 AS km,
                       searchable(matched.name) AS plain_name,
                       searchable(matched.name || ' ' || coalesce(matched.suburb, ''))
                           AS plain_name_and_suburb
                FROM (SELECT name, address, suburb, locality, latitude, longitude, confidence, location
                      FROM poi
                      WHERE search_text ILIKE searchable(:pattern)
                        AND confidence >= :minConfidence
                      LIMIT :scan) AS matched, term
                ORDER BY km
                LIMIT :candidates
            ),
            found AS (
                (SELECT 'ADDRESS' AS kind, full_address AS name,
                        NULL AS address, NULL AS suburb, NULL AS locality,
                        latitude, longitude, 1.0 AS confidence,
                        least(1.0, word_similarity(term.words, search_text) + term.slack)
                            * CASE WHEN starts_with(lower(replace(search_text, ',', '')), term.words)
                                   THEN 1.0 ELSE 0.8 END
                            * coalesce(0.5 + 0.5 / (1 + round(km * 2) / 2 / 50.0), 1.0) AS score,
                        similarity(search_text, term.words) AS coverage
                 FROM nearest_address, term
                 ORDER BY score DESC, coverage DESC, length(full_address)
                 LIMIT :limit)
                UNION ALL
                (SELECT 'POI', name, address, suburb, locality,
                        latitude, longitude, confidence,
                        least(1.0, word_similarity(term.words, plain_name_and_suburb) + term.slack)
                            * coalesce(0.5 + 0.5 / (1 + round(km * 2) / 2 / 50.0), 1.0) AS score,
                        greatest(similarity(plain_name, term.words),
                                 similarity(plain_name_and_suburb, term.words)) AS coverage
                 FROM nearest_poi, term
                 ORDER BY score DESC, coverage DESC, confidence DESC, length(name)
                 LIMIT :limit)
            )
            SELECT kind, name, address, suburb, locality, latitude, longitude
            FROM found
            ORDER BY score DESC, coverage DESC, confidence DESC, length(name), name
            LIMIT :limit
            """;

    private final JdbcClient jdbc;
    private final SearchProperties properties;

    public PostgresPlaceSearch(JdbcClient jdbc, SearchProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Override
    public List<FoundPlace> search(String typed, ReferencePoint near, int limit) {
        Optional<SearchTerm> term = SearchTerm.from(typed);
        if (term.isEmpty()) {
            return List.of();
        }

        // The nearest candidates can only be picked from matches that were looked at. With no
        // reference point nothing is nearest, so looking at more than will be ranked is wasted.
        int candidates = properties.candidateLimit();
        int scan = near == null ? candidates : Math.max(properties.scanLimit(), candidates);

        return jdbc.sql(SEARCH)
                .param("words", term.get().words())
                .param("pattern", term.get().pattern())
                // Typed, because a bare null gives PostgreSQL nothing to tell its type from.
                .param("latitude", near == null ? null : near.latitude(), Types.DOUBLE)
                .param("longitude", near == null ? null : near.longitude(), Types.DOUBLE)
                .param("minConfidence", properties.minConfidence())
                .param("scan", scan)
                .param("candidates", candidates)
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
