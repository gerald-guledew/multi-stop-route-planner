package com.solvelify.routeplanner.distance;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.PointList;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.solvelify.routeplanner.planning.GeoPoint;
import com.solvelify.routeplanner.planning.Location;
import com.solvelify.routeplanner.planning.TravelMatrix;
import com.solvelify.routeplanner.planning.TravelMatrixProvider;
import com.solvelify.routeplanner.planning.UnroutablePlaceException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fills the travel matrix with real driving distances from OpenStreetMap data.
 *
 * <p>The difference from straight lines is not cosmetic. Devonport to Sylvia Park measures about
 * 10 km across the harbour and far more by road, because the drive goes round by the bridge.
 * Orders that looked sensible on straight lines can be plainly wrong once water and motorways
 * are taken into account.
 *
 * <p>Both directions of every pair are calculated. One-way streets and motorway ramps mean the
 * drive from A to B is not always the same length as the drive back, which is a property straight
 * lines can never have.
 */
@Component
@ConditionalOnProperty(name = "routeplanner.routing.provider", havingValue = RoutingProperties.GRAPHHOPPER)
public class GraphHopperTravelMatrixProvider implements TravelMatrixProvider {

    private static final double METRES_PER_KM = 1000.0;

    private final GraphHopper graphHopper;
    private final String profile;

    public GraphHopperTravelMatrixProvider(GraphHopper graphHopper, RoutingProperties properties) {
        this.graphHopper = graphHopper;
        this.profile = properties.profile();
    }

    @Override
    public TravelMatrix matrixFor(List<Location> places) {
        int size = places.size();
        double[][] distances = new double[size][size];

        for (int from = 0; from < size; from++) {
            for (int to = 0; to < size; to++) {
                if (from != to) {
                    distances[from][to] = drivingDistanceKm(places.get(from), places.get(to));
                }
            }
        }

        return new TravelMatrix(distances);
    }

    @Override
    public List<GeoPoint> pathBetween(Location from, Location to) {
        ResponsePath path = bestPath(from, to);

        PointList points = path.getPoints();
        List<GeoPoint> shape = new ArrayList<>(points.size());
        for (int index = 0; index < points.size(); index++) {
            shape.add(new GeoPoint(points.getLat(index), points.getLon(index)));
        }
        return shape;
    }

    private double drivingDistanceKm(Location from, Location to) {
        return bestPath(from, to).getDistance() / METRES_PER_KM;
    }

    private ResponsePath bestPath(Location from, Location to) {
        GHRequest request = new GHRequest(
                from.latitude(), from.longitude(),
                to.latitude(), to.longitude())
                .setProfile(profile);

        GHResponse response = graphHopper.route(request);
        if (response.hasErrors()) {
            throw asUnroutable(response.getErrors().get(0), from, to);
        }

        return response.getBest();
    }

    /**
     * Turns a GraphHopper failure into something a caller can act on.
     *
     * <p>The common case is a coordinate dropped away from any road, which GraphHopper reports as
     * a point it cannot find. Naming the place matters: "cannot route to Airport" tells the user
     * which pin to move, while "no route found" tells them nothing.
     */
    private static RuntimeException asUnroutable(Throwable error, Location from, Location to) {
        if (error instanceof PointNotFoundException pointNotFound) {
            Location offending = pointNotFound.getPointIndex() == 0 ? from : to;
            return new UnroutablePlaceException(offending.name(),
                    "No road was found near those coordinates. Move the point closer to a street.");
        }
        return new UnroutablePlaceException(from.name() + " to " + to.name(), error.getMessage());
    }
}
