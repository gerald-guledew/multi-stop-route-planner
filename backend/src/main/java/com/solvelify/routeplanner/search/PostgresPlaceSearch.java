package com.solvelify.routeplanner.search;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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
     * <p><b>What was typed may be written more than one way.</b> "mt eden rd" is also "mount
     * eden road", and {@link SearchTerm} lists each such spelling. A place is found when it
     * matches any of them, which {@code :anySpelling} tests in one go. It is then scored on
     * each spelling it holds, and the best one counts, so "12 Bassett Road" ranks the same
     * whether "road" or "rd" was typed. It is not scored on the spellings it does not hold.
     * Those are not what found it, and scoring is the costly part. {@code :oneSpelling} saves
     * even the asking when there is nothing to choose between.
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
     * <p>A word in between costs nothing, unless something found is called just what was
     * typed. No place is called Auckland Hospital, and the hospital is Auckland City Hospital.
     * As a run, "City" costs as much as a word nobody typed, and the foundation, the wards
     * and the post boxes called Auckland Hospital Something would all come first. So a name
     * that holds the words in the order typed is also scored on each word on its own,
     * averaged, and the better of the two counts. "the warehouse" does name a shop, and there
     * the words count only together, because The Rug Warehouse is another shop. {@code named}
     * asks that once of everything found: whether a name, alone or with its suburb, is the
     * words typed and nothing else, with or without the spaces between them, and the last
     * perhaps unfinished. The words on their own are looked for in the name and not in the
     * suburb, or "the parnell" would match everything in Parnell with "The" in its name.
     *
     * <p>The last word is usually unfinished while somebody is typing, and an unfinished word
     * loses exactly one trigram, the one that marks where it ends. {@code slack} gives that
     * trigram back, so "mcdonald" finds McDonald's as surely as it finds a firm called
     * McDonald Metals. A word on its own gets the same back from {@code last_word_slack}.
     *
     * <p>Second, whether it is the place typed. An address that begins with the words keeps
     * its whole score, and one that only contains them keeps four fifths. So "12 queen
     * street" puts number 12 ahead of 112 and of 12/125, and the houses on McDonalds Road do
     * not bury the restaurants. A business keeps its whole score when a word of its name
     * begins with the first word typed, and four fifths when the words found it by its suburb
     * or its street alone. So "st heliers" lists what is named for Saint Heliers ahead of
     * whatever happens to be there, and "mobil st heliers" still finds the Mobil in full. A
     * business whose name and suburb lack a word typed keeps four fifths as well, having been
     * found partly by the street it is on. So "bank street whangarei" lists the addresses
     * ahead of a hairdresser with Bank in its name, on Robert St.
     *
     * <p>Third, how near the place is, when the caller said where it is looking from. Next
     * door keeps the whole score, 50 km away keeps three quarters, and nothing loses more
     * than half however far away it is. So distance decides between equal matches, and a
     * place that matches less than half as well can never overtake one that matches in full.
     * Distance counts in half kilometres. Places inside the same half kilometre are equally
     * near, which leaves the houses of one street, or the shops of one mall, to be ordered
     * by the tie-break rather than by which gate is a few metres closer. Where it is looking
     * from is a point, or a road, and then near means near any part of it.
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
     * <p>{@code spelling}, {@code nearest_poi} and {@code named} are materialized so that what
     * they work out is worked out once: what was typed and whether anything is called that
     * once for the query, and the simplified names once for each place rather than once for
     * each comparison that reads them.
     *
     * <p>Both sides of every comparison go through {@code searchable}, which drops accents
     * and apostrophes, so "paknsave mangere" finds PAK'nSAVE in Māngere. The patterns go
     * through it too. {@link SearchTerm#isLookedFor} sees to it that nothing in them comes
     * out as something a pattern would read.
     */
    private static final String SEARCH = """
            WITH term AS MATERIALIZED (
                SELECT CAST(ST_GeomFromText(:origin, 4326) AS geography) AS origin,
                       searchable(:firstWord) AS first_word
            ),
            spelling AS MATERIALIZED (
                SELECT spelt.*,
                       1.0 / greatest(cardinality(show_trgm(spelt.words)), 1) AS slack,
                       1.0 / greatest(cardinality(show_trgm(spelt.each_word[cardinality(spelt.each_word)])), 1)
                           AS last_word_slack
                FROM (SELECT lower(searchable(written.words)) AS words,
                             searchable(written.pattern) AS pattern,
                             searchable(written.name) AS whole_name,
                             ARRAY(SELECT word
                                   FROM unnest(string_to_array(lower(searchable(written.words)), ' ')) AS word
                                   WHERE cardinality(show_trgm(word)) > 0) AS each_word
                      FROM unnest(CAST(:words AS text[]), CAST(:patterns AS text[]), CAST(:names AS text[]))
                          AS written(words, pattern, name)) AS spelt
            ),
            nearest_address AS (
                SELECT matched.*, ST_Distance(matched.location, term.origin, false) / 1000 AS km
                FROM (SELECT full_address, search_text, latitude, longitude, location
                      FROM address
                      WHERE search_text ~* searchable(:anySpelling)
                      LIMIT :scan) AS matched, term
                ORDER BY km
                LIMIT :candidates
            ),
            nearest_poi AS MATERIALIZED (
                SELECT matched.*, ST_Distance(matched.location, term.origin, false) / 1000 AS km,
                       searchable(matched.name) AS plain_name,
                       searchable(matched.name || ' ' || coalesce(matched.suburb, ''))
                           AS plain_name_and_suburb
                FROM (SELECT name, address, suburb, locality, search_text,
                             latitude, longitude, confidence, location
                      FROM poi
                      WHERE search_text ~* searchable(:anySpelling)
                        AND confidence >= :minConfidence
                      LIMIT :scan) AS matched, term
                ORDER BY km
                LIMIT :candidates
            ),
            named AS MATERIALIZED (
                SELECT EXISTS (SELECT 1 FROM nearest_poi, spelling
                               WHERE nearest_poi.plain_name ~* spelling.whole_name
                                  OR nearest_poi.plain_name_and_suburb ~* spelling.whole_name) AS anything
            ),
            found AS (
                (SELECT 'ADDRESS' AS kind, full_address AS name,
                        NULL AS address, NULL AS suburb, NULL AS locality,
                        latitude, longitude, 1.0 AS confidence,
                        best.match * coalesce(0.5 + 0.5 / (1 + round(km * 2) / 2 / 50.0), 1.0) AS score,
                        best.coverage
                 FROM nearest_address
                 CROSS JOIN LATERAL (
                     SELECT max(least(1.0, word_similarity(spelling.words, search_text) + spelling.slack)
                                * CASE WHEN starts_with(lower(replace(search_text, ',', '')), spelling.words)
                                       THEN 1.0 ELSE 0.8 END) AS match,
                            max(similarity(search_text, spelling.words)) AS coverage
                     FROM spelling
                     WHERE :oneSpelling OR search_text ~* spelling.pattern) AS best
                 ORDER BY score DESC, coverage DESC, length(full_address)
                 LIMIT :limit)
                UNION ALL
                (SELECT 'POI', name, address, suburb, locality,
                        latitude, longitude, confidence,
                        best.match
                            * CASE WHEN plain_name ~* term.first_word THEN 1.0 ELSE 0.8 END
                            * coalesce(0.5 + 0.5 / (1 + round(km * 2) / 2 / 50.0), 1.0) AS score,
                        best.coverage
                 FROM nearest_poi
                 CROSS JOIN term
                 CROSS JOIN named
                 CROSS JOIN LATERAL (
                     SELECT max(greatest(held.together, held.apart) * held.every_word) AS match,
                            max(held.coverage) AS coverage
                     FROM spelling
                     CROSS JOIN LATERAL (
                         SELECT least(1.0, word_similarity(spelling.words, plain_name_and_suburb) + spelling.slack)
                                    AS together,
                                CASE WHEN named.anything OR cardinality(spelling.each_word) < 2
                                          OR plain_name !~* spelling.pattern THEN 0
                                     ELSE (SELECT avg(least(1.0, word_similarity(typed.word, plain_name)
                                                                 + CASE WHEN typed.n = cardinality(spelling.each_word)
                                                                        THEN spelling.last_word_slack ELSE 0 END))
                                           FROM unnest(spelling.each_word) WITH ORDINALITY AS typed(word, n))
                                END AS apart,
                                CASE WHEN plain_name_and_suburb ~* spelling.pattern THEN 1.0 ELSE 0.8 END
                                    AS every_word,
                                greatest(similarity(plain_name, spelling.words),
                                         similarity(plain_name_and_suburb, spelling.words)) AS coverage) AS held
                     WHERE :oneSpelling OR search_text ~* spelling.pattern) AS best
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

    /**
     * An address typed with a flat in front of it can find nothing though the building is
     * there, because LINZ lists the flats of some buildings and not of others. So when the
     * words as typed find nothing, {@link Unit} says what else to look for. Never otherwise:
     * what holds everything typed is the better answer.
     */
    @Override
    public List<FoundPlace> search(String typed, ReferencePoint near, int limit) {
        return searchFrom(typed, near == null ? null : pointAt(near), limit);
    }

    @Override
    public List<FoundPlace> searchAlong(String typed, List<ReferencePoint> road, int limit) {
        return searchFrom(typed, lineThrough(road), limit);
    }

    /** @param origin where the search is looking from, as text PostGIS reads. Null for nowhere */
    private List<FoundPlace> searchFrom(String typed, String origin, int limit) {
        List<FoundPlace> found = find(typed, origin, limit);

        Iterator<String> otherWays = Unit.otherWaysToFind(typed).iterator();
        while (found.isEmpty() && otherWays.hasNext()) {
            found = find(otherWays.next(), origin, limit);
        }
        return found;
    }

    private List<FoundPlace> find(String typed, String origin, int limit) {
        Optional<SearchTerm> term = SearchTerm.from(typed);
        if (term.isEmpty()) {
            return List.of();
        }

        // The nearest candidates can only be picked from matches that were looked at. With no
        // reference point nothing is nearest, so looking at more than will be ranked is wasted.
        int candidates = properties.candidateLimit();
        int scan = origin == null ? candidates : Math.max(properties.scanLimit(), candidates);

        List<SearchTerm.Spelling> spellings = term.get().spellings();

        return jdbc.sql(SEARCH)
                .param("words", spellings.stream().map(SearchTerm.Spelling::words).toArray(String[]::new))
                .param("patterns", spellings.stream().map(SearchTerm.Spelling::pattern).toArray(String[]::new))
                .param("names", spellings.stream().map(SearchTerm.Spelling::name).toArray(String[]::new))
                .param("anySpelling", term.get().anySpelling())
                .param("firstWord", term.get().firstWord())
                .param("oneSpelling", spellings.size() == 1)
                // Typed, because a bare null gives PostgreSQL nothing to tell its type from.
                .param("origin", origin, Types.VARCHAR)
                .param("minConfidence", properties.minConfidence())
                .param("scan", scan)
                .param("candidates", candidates)
                .param("limit", limit)
                .query(PostgresPlaceSearch::toFoundPlace)
                .list();
    }

    /** A point as PostGIS reads it from text: longitude first. */
    private static String pointAt(ReferencePoint point) {
        return "POINT(" + point.longitude() + " " + point.latitude() + ")";
    }

    /** A road as a line through its points. Null for no points, and a point for one. */
    private static String lineThrough(List<ReferencePoint> road) {
        if (road.isEmpty()) {
            return null;
        }
        if (road.size() == 1) {
            return pointAt(road.getFirst());
        }
        return road.stream()
                .map(point -> point.longitude() + " " + point.latitude())
                .collect(Collectors.joining(", ", "LINESTRING(", ")"));
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
