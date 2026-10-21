package com.solvelify.routeplanner.distance;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.reader.dem.SkadiProvider;
import com.graphhopper.reader.dem.TileBasedElevationProvider;
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import com.solvelify.routeplanner.planning.PlanningProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * The hint on a profile that has the engine count climb as road: how many metres of road a
     * metre of climb counts as.
     */
    private static final String ROAD_METRES_PER_METRE_CLIMBED = "road_metres_per_metre_climbed";

    /** The hint on that profile saying which version of the weighting prepared it. */
    private static final String CLIMB_WEIGHTING_VERSION = "climb_counted_as_road_version";

    @Bean(destroyMethod = "close")
    GraphHopper graphHopper(RoutingProperties properties, PlanningProperties planning) {
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
            hopper = engineFor(osmFile, heights.graphCache(), properties.profile(), heightsOfTheGround(heights.files()),
                    planning.roadMetresPerMetreClimbed());
        } else {
            log.info("Preparing GraphHopper from {}. The first run takes minutes, later runs are quick.", osmFile);
            hopper = engineFor(osmFile, properties.graphCache(), properties.profile(), ElevationProvider.NOOP,
                    planning.roadMetresPerMetreClimbed());
        }
        hopper.importOrLoad();

        log.info("GraphHopper ready, routing on the profiles {}",
                hopper.getProfiles().stream().map(Profile::getName).toList());
        return hopper;
    }

    /** The engine as this application sets it up, with a metre of climb counted as it ships. */
    static GraphHopper engineFor(Path osmFile, String graphFolder, String profile, ElevationProvider heights) {
        return engineFor(osmFile, graphFolder, profile, heights, new PlanningProperties(null).roadMetresPerMetreClimbed());
    }

    /**
     * The engine as this application sets it up, not yet loaded.
     *
     * <p>Apart from the bean so that a test can have the very same engine on a map of its own,
     * with heights it made up.
     *
     * <p>With heights it weighs roads two ways, each a profile prepared ahead: its own way, and
     * with every metre a road climbs counted as so many metres more of it. That figure and the
     * version of the weighting are part of the second profile, so a graph prepared with another
     * figure or an older weighting is refused, not read.
     *
     * @param heights                   where the height of a point comes from, or
     *                                  {@link ElevationProvider#NOOP} for roads taken as flat
     * @param roadMetresPerMetreClimbed how many metres of road a metre of climb counts as, on the
     *                                  roads that climb less. Not read without heights
     */
    static GraphHopper engineFor(
            Path osmFile, String graphFolder, String profile, ElevationProvider heights, double roadMetresPerMetreClimbed) {
        GraphHopper hopper = new EngineCountingClimb();
        hopper.setOSMFile(osmFile.toString());
        hopper.setGraphHopperLocation(graphFolder);
        hopper.setEncodedValuesString("car_access, car_average_speed, road_access");

        List<Profile> profiles = new ArrayList<>();
        profiles.add(new Profile(profile).setCustomModel(GHUtility.loadCustomModelFromJar("car.json")));
        if (heights != ElevationProvider.NOOP) {
            profiles.add(new Profile(climbingLess(profile))
                    .setCustomModel(GHUtility.loadCustomModelFromJar("car.json"))
                    .putHint(ROAD_METRES_PER_METRE_CLIMBED, roadMetresPerMetreClimbed)
                    .putHint(CLIMB_WEIGHTING_VERSION, ClimbCountedAsRoad.VERSION));
        }
        hopper.setProfiles(profiles);
        // Contraction hierarchies: slower to prepare once, far faster on every query afterwards.
        hopper.getCHPreparationHandler().setCHProfiles(
                profiles.stream().map(each -> new CHProfile(each.getName())).toList());

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

    /** The name of the profile whose roads climb less, beside the one named in the settings. */
    static String climbingLess(String profile) {
        return profile + "_climbing_less";
    }

    /**
     * The engine, which weighs roads by their climb as well for a profile that says how much a
     * metre of climb counts. It does so whenever it weighs them, preparing the profile ahead as
     * much as answering, so the two cannot differ.
     */
    private static final class EngineCountingClimb extends GraphHopper {

        @Override
        protected WeightingFactory createWeightingFactory() {
            WeightingFactory own = super.createWeightingFactory();
            return (profile, hints, disableTurnCosts) -> {
                Weighting weighting = own.createWeighting(profile, hints, disableTurnCosts);
                PMap asked = profile.getHints();
                return asked.has(ROAD_METRES_PER_METRE_CLIMBED)
                        ? new ClimbCountedAsRoad(weighting, asked.getDouble(ROAD_METRES_PER_METRE_CLIMBED, 0))
                        : weighting;
            };
        }
    }
}
