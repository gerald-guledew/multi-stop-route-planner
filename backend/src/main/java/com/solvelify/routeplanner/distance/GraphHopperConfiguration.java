package com.solvelify.routeplanner.distance;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.GHUtility;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties(RoutingProperties.class)
@ConditionalOnProperty(name = "routeplanner.routing.provider", havingValue = RoutingProperties.GRAPHHOPPER)
class GraphHopperConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GraphHopperConfiguration.class);

    @Bean(destroyMethod = "close")
    GraphHopper graphHopper(RoutingProperties properties) {
        Path osmFile = Path.of(properties.osmFile());
        if (!Files.exists(osmFile)) {
            throw new IllegalStateException(
                    "Map extract not found at " + osmFile.toAbsolutePath()
                            + ". Download it from https://download.geofabrik.de/australia-oceania/new-zealand-latest.osm.pbf"
                            + " or set routeplanner.routing.provider=haversine");
        }

        log.info("Preparing GraphHopper from {}. The first run takes minutes, later runs are quick.", osmFile);

        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(osmFile.toString());
        hopper.setGraphHopperLocation(properties.graphCache());
        hopper.setEncodedValuesString("car_access, car_average_speed, road_access");
        hopper.setProfiles(new Profile(properties.profile())
                .setCustomModel(GHUtility.loadCustomModelFromJar("car.json")));
        // Contraction hierarchies: slower to prepare once, far faster on every query afterwards.
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(properties.profile()));
        hopper.importOrLoad();

        log.info("GraphHopper ready, routing on the {} profile", properties.profile());
        return hopper;
    }
}
