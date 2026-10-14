package com.solvelify.routeplanner.distance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.graphhopper.reader.dem.TileBasedElevationProvider;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The height at a pin, read from height files made up for the test.
 *
 * <p>A made-up file holds 11 by 11 heights, one every tenth of a degree, where a real one holds
 * 3,601 by 3,601. The reader works out the spacing from the size, so the arithmetic below is
 * the same as on a real file and easier to follow. The ground rises 10 m with every tenth of a
 * degree east, and falls 100 m with every tenth of a degree south.
 */
class HeightFilesTest {

    private static final int GRID = 11;

    @TempDir
    Path folder;

    @Test
    void readsTheHeightAtAPointOfTheGrid() throws IOException {
        writeAFile("S37E174.hgt.gz", 1000);

        // Five tenths east, two tenths south of the north-west corner.
        assertThat(new HeightFiles(folder).at(-36.2, 174.5).getAsDouble()).isCloseTo(850, within(0.01));
    }

    @Test
    void worksOutAHeightBetweenPointsOfTheGrid() throws IOException {
        writeAFile("S37E174.hgt.gz", 1000);

        // Halfway between grid points both ways, so halfway between their heights.
        assertThat(new HeightFiles(folder).at(-36.25, 174.55).getAsDouble()).isCloseTo(805, within(0.01));
    }

    @Test
    void knowsNothingWhereNoFileIsHeld() throws IOException {
        writeAFile("S37E174.hgt.gz", 1000);

        assertThat(new HeightFiles(folder).at(-38.5, 175.5)).isEmpty();
    }

    @Test
    void fetchesNothingAndWritesNothingBesideTheFiles() throws IOException {
        writeAFile("S37E174.hgt.gz", 1000);
        HeightFiles heights = new HeightFiles(folder);

        heights.at(-36.2, 174.5);
        heights.at(-38.5, 175.5);

        assertThat(filesIn(folder)).containsExactly("S37E174.hgt.gz");
    }

    @Test
    void itsReaderRefusesToFetchAFile() {
        TileBasedElevationProvider reader = HeightFiles.readerOf(folder);
        // A port where nothing listens. A fetch that got past the refusal fails here, and never
        // reaches the real source.
        reader.setBaseURL("http://127.0.0.1:9/");

        assertThatThrownBy(() -> reader.getEle(-38.5, 175.5))
                .rootCause()
                .hasMessageContaining("not fetched");
    }

    @Test
    void keepsAnsweringAfterLettingGoOfTheFilesItHeld() throws IOException {
        List<String> files = List.of(
                "S37E174.hgt.gz", "S37E175.hgt.gz", "S38E174.hgt.gz", "S38E175.hgt.gz", "S36E174.hgt.gz");
        for (int i = 0; i < files.size(); i++) {
            writeAFile(files.get(i), 1000 * (i + 1));
        }
        HeightFiles heights = new HeightFiles(folder);

        // Five tenths east and two south of each square's north-west corner: 150 m below it.
        assertThat(heights.at(-36.2, 174.5).getAsDouble()).isCloseTo(850, within(0.01));
        assertThat(heights.at(-36.2, 175.5).getAsDouble()).isCloseTo(1850, within(0.01));
        assertThat(heights.at(-37.2, 174.5).getAsDouble()).isCloseTo(2850, within(0.01));
        assertThat(heights.at(-37.2, 175.5).getAsDouble()).isCloseTo(3850, within(0.01));
        // A fifth file: the four held so far are let go.
        assertThat(heights.at(-35.2, 174.5).getAsDouble()).isCloseTo(4850, within(0.01));
        assertThat(heights.at(-36.25, 174.55).getAsDouble()).isCloseTo(805, within(0.01));
    }

    @Test
    void namesAFileForTheSouthWestCornerOfItsSquare() {
        assertThat(HeightFiles.fileFor(-36.8778, 174.7639)).isEqualTo("S37E174.hgt.gz");
        // The Chatham Islands, west of the line where the longitude turns.
        assertThat(HeightFiles.fileFor(-43.95, -176.55)).isEqualTo("S44W177.hgt.gz");
        assertThat(HeightFiles.fileFor(51.5, -0.12)).isEqualTo("N51W001.hgt.gz");
        assertThat(HeightFiles.fileFor(-8.5, 5.5)).isEqualTo("S09E005.hgt.gz");
    }

    @Test
    void knowsNothingAnywhereWithHeightsOff() {
        assertThat(GroundHeights.NONE.at(-36.8778, 174.7639)).isEmpty();
    }

    /**
     * A height file as the terrain tiles have it: gzipped heights in whole metres, two bytes
     * each, most significant first, one row after another from the north edge down. Its name
     * says the square: the north edge of {@code S37E174} is 36 degrees south.
     */
    private void writeAFile(String name, int northWestHeight) throws IOException {
        try (OutputStream file = Files.newOutputStream(folder.resolve(name));
                DataOutputStream heights = new DataOutputStream(new GZIPOutputStream(file))) {
            for (int row = 0; row < GRID; row++) {
                for (int column = 0; column < GRID; column++) {
                    heights.writeShort(northWestHeight + 10 * column - 100 * row);
                }
            }
        }
    }

    private static List<String> filesIn(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }
}
