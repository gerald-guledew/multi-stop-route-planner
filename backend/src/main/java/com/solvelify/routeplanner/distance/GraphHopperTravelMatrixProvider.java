package com.solvelify.routeplanner.distance;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.solvelify.routeplanner.planning.ClimbAndDescent;
import com.solvelify.routeplanner.planning.Directions;
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
 * Fills the travel matrix with real driving distances and times from OpenStreetMap data.
 *
 * <p>The difference from straight lines is not cosmetic. Devonport to Sylvia Park measures about
 * 10 km across the harbour and far more by road, because the drive goes round by the bridge.
 * Orders that looked sensible on straight lines can be plainly wrong once water and motorways
 * are taken into account.
 *
 * <p>Both directions of every pair are calculated. One-way streets and motorway ramps mean the
 * drive from A to B is not always the same length as the drive back, which is a property straight
 * lines can never have.
 *
 * <p>The time is what each road's speed allows with nothing in the way. OpenStreetMap carries no
 * traffic, so at five in the afternoon the real drive takes longer than this says.
 *
 * <p>How much a drive climbs and descends is given only when the graph was prepared with the
 * height of every road. The map itself says where a road runs and not how high it is. The
 * engine's own totals for climb and descent are not used: see {@link HeightProfile} for why,
 * and for what is counted instead.
 *
 * <p>A graph with heights also measures every road along its slope, bumps of the grid included.
 * On the real map that made a gorge road 1.3% longer than it measures on the map. So with heights
 * the distance is still taken off the map, as it is without them, and switching heights on does
 * not move it.
 *
 * <p>How much of a drive is in town is read off the speed the engine drives each stretch at, so
 * the fuel can take a town figure there and the open road's elsewhere.
 */
@Component
@ConditionalOnProperty(name = "routeplanner.routing.provider", havingValue = RoutingProperties.GRAPHHOPPER)
public class GraphHopperTravelMatrixProvider implements TravelMatrixProvider {

    private static final double METRES_PER_KM = 1000.0;
    private static final double MILLIS_PER_SECOND = 1000.0;

    private static final boolean EVERY_POINT = true;
    private static final boolean THINNED_FOR_DRAWING = false;

    /**
     * A stretch the engine drives at this speed or less counts as town for the fuel, and a faster
     * one as the open road. The engine drives a road at 90% of its limit, so this puts a limit of
     * 60 km/h or less in town and one of 70 or more on the open road.
     */
    static final double TOWN_KM_PER_HOUR = 60;

    private final GraphHopper graphHopper;
    private final String profile;
    private final boolean roadsHaveHeights;

    public GraphHopperTravelMatrixProvider(GraphHopper graphHopper, RoutingProperties properties) {
        this.graphHopper = graphHopper;
        this.profile = properties.profile();
        // Asked of the graph and not of the settings. The graph is what the figures come from.
        this.roadsHaveHeights = graphHopper.getBaseGraph().getNodeAccess().is3D();
    }

    @Override
    public TravelMatrix matrixFor(List<Location> places) {
        int size = places.size();
        double[][] distances = new double[size][size];
        double[][] durations = new double[size][size];
        ClimbAndDescent[][] climbs = new ClimbAndDescent[size][size];
        double[][] kmInTown = new double[size][size];

        for (int from = 0; from < size; from++) {
            for (int to = 0; to < size; to++) {
                if (from == to) {
                    climbs[from][to] = ClimbAndDescent.LEVEL;
                } else if (roadsHaveHeights) {
                    // One drive worked out, and every figure read off it.
                    ResponsePath drive = bestPath(places.get(from), places.get(to), EVERY_POINT);
                    Road road = Road.along(drive.getPoints());
                    distances[from][to] = road.metresOnTheMap() / METRES_PER_KM;
                    durations[from][to] = drive.getTime() / MILLIS_PER_SECOND;
                    climbs[from][to] = HeightProfile.climbAndDescent(road.metresAlong(), road.heights());
                    kmInTown[from][to] = distances[from][to] * shareInTown(road.metresAlong(), drive);
                } else {
                    ResponsePath drive = bestPath(places.get(from), places.get(to), THINNED_FOR_DRAWING);
                    distances[from][to] = drive.getDistance() / METRES_PER_KM;
                    durations[from][to] = drive.getTime() / MILLIS_PER_SECOND;
                    kmInTown[from][to] = distances[from][to] * shareInTown(metresAlong(drive.getPoints()), drive);
                }
            }
        }

        // Without heights there is nothing to say about climb, which is not the same as a level
        // road. The engine itself answers nought for every drive on such a graph.
        return (roadsHaveHeights
                ? new TravelMatrix(distances, durations, climbs)
                : new TravelMatrix(distances, durations))
                .withKmInTown(kmInTown);
    }

    @Override
    public Directions directionsBetween(Location from, Location to) {
        // The names of the roads along the drive too, for the turns onto a road the map does
        // not name.
        GHRequest request = requestFor(from, to, profile)
                .setPathDetails(List.of(Parameters.Details.STREET_NAME, Parameters.Details.STREET_REF));
        ResponsePath drive = route(request, from, to);

        PointList points = drive.getPoints();
        List<GeoPoint> shape = new ArrayList<>(points.size());
        for (int index = 0; index < points.size(); index++) {
            shape.add(new GeoPoint(points.getLat(index), points.getLon(index)));
        }
        // Worked out by the engine along with the drive, from all of its points.
        return new Directions(shape, TurnsFromTheEngine.from(drive.getInstructions(), points, drive.getPathDetails()));
    }

    /**
     * @param everyPoint whether the answer keeps every point of the drive. Left alone the engine
     *                   drops any point the line can be drawn without, judging by the map alone,
     *                   and the heights of a straight road go with them
     */
    private ResponsePath bestPath(Location from, Location to, boolean everyPoint) {
        GHRequest request = requestFor(from, to, profile)
                .setPathDetails(List.of(Parameters.Details.AVERAGE_SPEED));
        if (everyPoint) {
            request.putHint(Parameters.Routing.WAY_POINT_MAX_DISTANCE, 0);
        }
        return route(request, from, to);
    }

    private static GHRequest requestFor(Location from, Location to, String profile) {
        return new GHRequest(
                from.latitude(), from.longitude(),
                to.latitude(), to.longitude())
                .setProfile(profile);
    }

    private ResponsePath route(GHRequest request, Location from, Location to) {
        GHResponse response = graphHopper.route(request);
        if (response.hasErrors()) {
            throw asUnroutable(response.getErrors().get(0), from, to);
        }

        return response.getBest();
    }

    /**
     * The share of a drive on town roads, from the speed the engine gives each stretch of it.
     * Measured along its points, so it holds whichever way the drive itself was measured.
     */
    private static double shareInTown(double[] metresAlong, ResponsePath drive) {
        double total = metresAlong.length == 0 ? 0 : metresAlong[metresAlong.length - 1];
        if (total == 0) {
            return 0;
        }
        double inTown = 0;
        for (PathDetail stretch : drive.getPathDetails().getOrDefault(Parameters.Details.AVERAGE_SPEED, List.of())) {
            if (stretch.getValue() instanceof Number speed && speed.doubleValue() <= TOWN_KM_PER_HOUR) {
                inTown += metresAlong[stretch.getLast()] - metresAlong[stretch.getFirst()];
            }
        }
        return inTown / total;
    }

    /** How far along a drive each of its points is, measured on the map. */
    private static double[] metresAlong(PointList points) {
        double[] metresAlong = new double[points.size()];
        for (int at = 1; at < points.size(); at++) {
            metresAlong[at] = metresAlong[at - 1] + DistanceCalcEarth.DIST_EARTH.calcDist(
                    points.getLat(at - 1), points.getLon(at - 1),
                    points.getLat(at), points.getLon(at));
        }
        return metresAlong;
    }

    /**
     * One drive as a line of points: how far along the drive each one is, measured on the map,
     * and how high it is.
     */
    private record Road(double[] metresAlong, double[] heights) {

        static Road along(PointList points) {
            double[] heights = new double[points.size()];
            for (int at = 0; at < points.size(); at++) {
                heights[at] = points.getEle(at);
            }
            return new Road(GraphHopperTravelMatrixProvider.metresAlong(points), heights);
        }

        double metresOnTheMap() {
            return metresAlong.length == 0 ? 0 : metresAlong[metresAlong.length - 1];
        }
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
