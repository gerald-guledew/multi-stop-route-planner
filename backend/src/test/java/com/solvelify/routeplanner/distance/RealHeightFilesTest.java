package com.solvelify.routeplanner.distance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The height at a pin, read from the real height files, against heights that are published.
 *
 * <p>Needs the height files, which exist only where somebody has started the application once
 * with {@code routeplanner.routing.heights.enabled=true}. Anywhere else this test skips itself.
 * It never fetches a file.
 *
 * <p>Measured on 2026-10-15 with the GDAL in Postgres.app, at the grid point nearest each
 * summit: Maungawhau 195 m, published as 196 m. Maungakiekie 174 m, published as 182 m.
 * Rangitoto 252 m, published as 260 m. The heights are LINZ's 8 m model of 2012, made from 20 m
 * contours, and a summit between two contours is cut short.
 */
class RealHeightFilesTest {

    private static final Path FOLDER = Path.of("data/heights");
    private static final Path AUCKLAND = FOLDER.resolve("S37E174.hgt.gz");

    private static HeightFiles heights;

    @BeforeAll
    static void readTheHeightFiles() {
        assumeTrue(Files.exists(AUCKLAND),
                "No height file at " + AUCKLAND.toAbsolutePath() + ", skipping the tests on real heights");
        heights = new HeightFiles(FOLDER);
    }

    @Test
    void readsTheTopOfMaungawhauCloseToItsPublishedHeight() {
        assertThat(heights.at(-36.8778, 174.7639).getAsDouble()).isCloseTo(196, within(5.0));
    }

    @Test
    void readsTheSeaAsNought() {
        // The Hauraki Gulf between Rangitoto and Waiheke, and the Waitematā off Herne Bay.
        assertThat(heights.at(-36.80, 174.90).getAsDouble()).isCloseTo(0, within(0.5));
        assertThat(heights.at(-36.833, 174.73).getAsDouble()).isCloseTo(0, within(0.5));
    }

    @Test
    void knowsNothingOutsideTheFilesHeldAndFetchesNothing() throws IOException {
        List<String> before = filesIn(FOLDER);

        // Sydney.
        assertThat(heights.at(-33.87, 151.21)).isEmpty();

        assertThat(filesIn(FOLDER)).isEqualTo(before);
    }

    private static List<String> filesIn(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }
}
