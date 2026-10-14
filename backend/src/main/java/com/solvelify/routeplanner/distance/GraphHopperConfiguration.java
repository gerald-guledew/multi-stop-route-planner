package com.solvelify.routeplanner.distance;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.reader.dem.SkadiProvider;
import com.graphhopper.reader.dem.TileBasedElevationProvider;
import com.graphhopper.util.GHUtility;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the routing engine, and only when it is actually asked for.
 *
 * <p>Kept apart from the provider so the provider stays a plain class that a test can hand a
 * ready-made engine. The engine is expensive: the first start reads the whole map extract and
 * writes a prepared graph to disk, which takes minutes. Later starts load that graph in seconds.
 */
@Configuration
@ConditionalOnProperty(name = "routeplanner.routing.provider", havingValue = RoutingProperties.GRAPHHOPPER)
class GraphHopperConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GraphHopperConfiguration.class);

    /**
     * The map says where a road bends and nothing about the ground between two bends. A straight
     * kilometre over a hill would be read as an even slope from one end to the other. So on a
     * stretch with no bend in it, a height is taken every this many metres.
     */
    private static final double HEIGHT_TAKEN_EVERY_METRES = 60;

    /**
     * The engine drops a point of a road when the road keeps its shape without it. Left to
     * itself it judges that shape seen from above, so the top of a hill on a straight road goes,
     * and the hill with it. With this set it keeps any point whose loss would move the road up or
     * down by more than this many metres.
     */
    private static final double HEIGHT_KEPT_TO_WITHIN_METRES = 1;

    @Bean(destroyMethod = "close")
    GraphHopper graphHopper(RoutingProperties properties) {
        Path osmFile = Path.of(properties.osmFile());
        if (!Files.exists(osmFile)) {
            throw new IllegalStateException(
                    "Map extract not found at " + osmFile.toAbsolutePath()
                            + ". Download it from https://download.geofabrik.de/australia-oceania/new-zealand-latest.osm.pbf"
                            + " or set routeplanner.routing.provider=haversine");
        }

        RoutingProperties.Heights heights = properties.heights();
        GraphHopper hopper;
        if (heights.enabled()) {
            log.info("Preparing GraphHopper from {}, with the height of every road. The first run fetches"
                    + " the height files into {} and takes minutes, later runs are quick.", osmFile, heights.files());
            hopper = engineFor(osmFile, heights.graphCache(), properties.profile(), heightsOfTheGround(heights.files()));
        } else {
            log.info("Preparing GraphHopper from {}. The first run takes minutes, later runs are quick.", osmFile);
            hopper = engineFor(osmFile, properties.graphCache(), properties.profile(), ElevationProvider.NOOP);
        }
        hopper.importOrLoad();

        log.info("GraphHopper ready, routing on the {} profile", properties.profile());
        return hopper;
    }

    /**
     * The engine as this application sets it up, not yet loaded.
     *
     * <p>Apart from the bean so that a test can have the very same engine on a map of its own,
     * with heights it made up.
     *
     * @param heights where the height of a point comes from, or {@link ElevationProvider#NOOP} for
     *                roads taken as flat
     */
    static GraphHopper engineFor(Path osmFile, String graphFolder, String profile, ElevationProvider heights) {
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(osmFile.toString());
        hopper.setGraphHopperLocation(graphFolder);
        hopper.setEncodedValuesString("car_access, car_average_speed, road_access");
        hopper.setProfiles(new Profile(profile)
                .setCustomModel(GHUtility.loadCustomModelFromJar("car.json")));
        // Contraction hierarchies: slower to prepare once, far faster on every query afterwards.
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(profile));

        if (heights != ElevationProvider.NOOP) {
            hopper.setElevationProvider(heights);
            hopper.getReaderConfig()
                    .setLongEdgeSamplingDistance(HEIGHT_TAKEN_EVERY_METRES)
                    .setElevationMaxWayPointDistance(HEIGHT_KEPT_TO_WITHIN_METRES);
        }
        return hopper;
    }

    /**
     * Heights of the ground on a grid of one second of arc, about 30 m, in files of one degree
     * square. GraphHopper fetches each file the first time a road falls in its square and keeps
     * it in the folder given, so a second build fetches nothing.
     *
     * <p>Between four grid points the height is worked out from all four. The nearest one alone
     * would turn an even slope into steps, and every step would count as climbing.
     *
     * <p>{@link HeightFiles} reads a pin's height with this same reader, so a pin and the roads
     * around it are read alike.
     */
    static TileBasedElevationProvider heightsOfTheGround(String folder) {
        return new SkadiProvider(folder).setInterpolate(true);
    }
}
