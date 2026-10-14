package com.solvelify.routeplanner.distance;

import com.graphhopper.reader.dem.TileBasedElevationProvider;
import com.graphhopper.storage.DAType;
import com.graphhopper.util.Downloader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the height of the ground from the height files the routing engine fetched, with the
 * reader the engine used. So a pin is given the height the roads around it were given.
 *
 * <p>Never fetches. A point whose file is not on this computer has no height here. The engine's
 * reader would go and get the file, so this looks for it first, and the reader is given a
 * downloader that refuses. Files are fetched in one place only: the first start with heights on.
 *
 * <p>A file takes 26 MB once read, so at most four are held at a time. Reading a fifth lets go
 * of the four. The pins of one trip seldom span more than two.
 */
class HeightFiles implements GroundHeights {

    private static final Logger log = LoggerFactory.getLogger(HeightFiles.class);

    private static final int MOST_FILES_HELD = 4;

    private static final Downloader REFUSES = new Downloader("route-planner") {
        @Override
        public HttpURLConnection createConnection(String url) throws IOException {
            throw new IOException("Height files are not fetched to answer for a pin: " + url);
        }

        @Override
        public void downloadFile(String url, String toFile) throws IOException {
            throw new IOException("Height files are not fetched to answer for a pin: " + url);
        }
    };

    private final Path folder;
    private final Set<String> held = new HashSet<>();
    private TileBasedElevationProvider reader;

    HeightFiles(Path folder) {
        this.folder = folder;
        this.reader = readerOf(folder);
    }

    /** One reader for every request. It was written for building a graph, by one thread. */
    @Override
    public synchronized OptionalDouble at(double latitude, double longitude) {
        String file = fileFor(latitude, longitude);
        if (!Files.isRegularFile(folder.resolve(file))) {
            return OptionalDouble.empty();
        }
        if (!held.contains(file) && held.size() == MOST_FILES_HELD) {
            reader.release();
            reader = readerOf(folder);
            held.clear();
        }
        held.add(file);

        try {
            double height = reader.getEle(latitude, longitude);
            return Double.isNaN(height) ? OptionalDouble.empty() : OptionalDouble.of(height);
        } catch (RuntimeException unreadable) {
            // The file name only. A position does not belong in a log.
            log.warn("Could not read a height from {}", file, unreadable);
            return OptionalDouble.empty();
        }
    }

    /**
     * The file holding a point: one degree square, named for its south-west corner, as the
     * files arrive from the terrain tiles. Auckland is in {@code S37E174.hgt.gz}.
     */
    static String fileFor(double latitude, double longitude) {
        int south = (int) Math.floor(latitude);
        int west = (int) Math.floor(longitude);
        return String.format(Locale.ROOT, "%s%02d%s%03d.hgt.gz",
                south < 0 ? "S" : "N", Math.abs(south),
                west < 0 ? "W" : "E", Math.abs(west));
    }

    static TileBasedElevationProvider readerOf(Path folder) {
        return GraphHopperConfiguration.heightsOfTheGround(folder.toString())
                // Kept in memory. Left to itself the reader writes the 26 MB it unpacks from a
                // file into a new file beside it.
                .setDAType(DAType.RAM)
                .setDownloader(REFUSES);
    }
}
