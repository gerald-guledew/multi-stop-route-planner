package com.solvelify.routeplanner.along;

import com.solvelify.routeplanner.planning.Drive;
import com.solvelify.routeplanner.planning.GeoPoint;
import com.solvelify.routeplanner.planning.Location;
import com.solvelify.routeplanner.planning.TravelMatrixProvider;
import com.solvelify.routeplanner.planning.UnroutablePlaceException;
import com.solvelify.routeplanner.search.FoundPlace;
import com.solvelify.routeplanner.search.PlaceSearch;
import com.solvelify.routeplanner.search.ReferencePoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

/**
 * Finds places to stop at on the way: those the roads of a planned trip pass near, each with what
 * stopping there adds to the leg it is a stop on.
 *
 * <p>The search finds places by what was typed and ranks them by how near they are to the roads,
 * in a straight line. Near is not the same as on the way. A shop across a motorway can be a long
 * way round, and one a little further off can sit on the road itself. So the places the search
 * ranks first are each driven to and on from, and given back in the order of the time they add,
 * or of the distance on straight lines.
 */
@Service
public class PlacesAlongTheRoute {

    /**
     * How many places the search hands over to be driven to. More than are given back, since the
     * nearest in a straight line are not always the quickest to stop at. Each costs two drives.
     */
    static final int CANDIDATES = 12;

    /**
     * Points of a road closer together than this add nothing to how near a place is to it. The
     * engine gives a point at every bend, a thousand on a long drive, and the search measures each
     * place it looks at against every point.
     */
    static final double THINNED_TO_METRES = 100;

    private final PlaceSearch placeSearch;
    private final TravelMatrixProvider travelMatrixProvider;

    public PlacesAlongTheRoute(PlaceSearch placeSearch, TravelMatrixProvider travelMatrixProvider) {
        this.placeSearch = placeSearch;
        this.travelMatrixProvider = travelMatrixProvider;
    }

    /**
     * @param route the places of a planned trip in the order they are driven to: the start first,
     *              and the start again last on a round trip
     * @param leg   the one leg to look along, counting from 0. Empty to look along them all. Each
     *              place is then a stop on the leg whose road passes nearest to it
     * @return the places in the order of what they add, the least first
     */
    public List<PlaceAlong> find(String typed, List<Location> route, OptionalInt leg, int limit) {
        if (route.size() < 2) {
            throw new IllegalArgumentException("a route has two places at least, was given " + route.size());
        }
        int legCount = route.size() - 1;
        if (leg.isPresent() && (leg.getAsInt() < 0 || leg.getAsInt() >= legCount)) {
            throw new IllegalArgumentException(
                    "there is no leg " + leg.getAsInt() + " on a route of " + legCount + " legs");
        }

        List<Integer> legsLookedAlong = leg.isPresent()
                ? List.of(leg.getAsInt())
                : IntStream.range(0, legCount).boxed().toList();
        Map<Integer, List<GeoPoint>> roads = new LinkedHashMap<>();
        for (int each : legsLookedAlong) {
            roads.put(each, thinned(roadOf(route.get(each), route.get(each + 1))));
        }

        List<ReferencePoint> road = roads.values().stream()
                .flatMap(List::stream)
                .map(point -> new ReferencePoint(point.latitude(), point.longitude()))
                .toList();
        List<FoundPlace> found = placeSearch.searchAlong(typed, road, Math.max(CANDIDATES, limit));

        // The leg as planned, driven once however many places are stops on it.
        Map<Integer, Drive> legsAsPlanned = new HashMap<>();
        List<PlaceAlong> along = new ArrayList<>(found.size());
        for (FoundPlace place : found) {
            int on = nearestLeg(place, roads);
            Location from = route.get(on);
            Location to = route.get(on + 1);
            Drive asPlanned = legsAsPlanned.computeIfAbsent(on, any -> travelMatrixProvider.driveBetween(from, to));
            Location stop = new Location(place.name(), place.latitude(), place.longitude());
            try {
                Drive there = travelMatrixProvider.driveBetween(from, stop);
                Drive onwards = travelMatrixProvider.driveBetween(stop, to);
                along.add(new PlaceAlong(
                        place,
                        on,
                        added(there.distanceKm() + onwards.distanceKm(), asPlanned.distanceKm()),
                        secondsAdded(there, onwards, asPlanned)));
            } catch (UnroutablePlaceException noRoadNearIt) {
                // Nobody can stop at a place with no road near it. The search knows nothing of roads.
            }
        }

        boolean timed = along.stream().allMatch(place -> place.secondsAdded().isPresent());
        // A stable sort, so places that add the same keep the order the search gave them.
        along.sort(timed
                ? Comparator.comparingDouble(place -> place.secondsAdded().getAsDouble())
                : Comparator.comparingDouble(PlaceAlong::kmAdded));
        return List.copyOf(along.subList(0, Math.min(limit, along.size())));
    }

    /** The road the leg follows, or a straight line between its ends when the provider has none. */
    private List<GeoPoint> roadOf(Location from, Location to) {
        List<GeoPoint> path = travelMatrixProvider.directionsBetween(from, to).path();
        return path.isEmpty()
                ? List.of(new GeoPoint(from.latitude(), from.longitude()), new GeoPoint(to.latitude(), to.longitude()))
                : path;
    }

    /** The road with only as many points as its shape needs. Its two ends are always kept. */
    static List<GeoPoint> thinned(List<GeoPoint> road) {
        if (road.size() <= 2) {
            return road;
        }
        List<GeoPoint> kept = new ArrayList<>();
        kept.add(road.getFirst());
        for (GeoPoint point : road.subList(1, road.size() - 1)) {
            if (Flat.metresBetween(kept.getLast(), point) >= THINNED_TO_METRES) {
                kept.add(point);
            }
        }
        kept.add(road.getLast());
        return kept;
    }

    /** The leg whose road passes nearest to the place, of those looked along. */
    private static int nearestLeg(FoundPlace place, Map<Integer, List<GeoPoint>> roads) {
        GeoPoint at = new GeoPoint(place.latitude(), place.longitude());
        int nearest = -1;
        double nearestMetres = Double.MAX_VALUE;
        for (Map.Entry<Integer, List<GeoPoint>> road : roads.entrySet()) {
            double metres = Flat.metresFromLine(at, road.getValue());
            if (metres < nearestMetres) {
                nearest = road.getKey();
                nearestMetres = metres;
            }
        }
        return nearest;
    }

    /**
     * Never below nought. A place on the road the leg follows anyway adds nothing, and the two
     * drives through it can come out a few metres shorter than the one past it, where the engine
     * meets the road at the place and not at the next point along.
     */
    private static double added(double withTheStop, double asPlanned) {
        return Math.max(0, withTheStop - asPlanned);
    }

    private static OptionalDouble secondsAdded(Drive there, Drive onwards, Drive asPlanned) {
        if (there.durationSeconds().isEmpty()
                || onwards.durationSeconds().isEmpty()
                || asPlanned.durationSeconds().isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(added(
                there.durationSeconds().getAsDouble() + onwards.durationSeconds().getAsDouble(),
                asPlanned.durationSeconds().getAsDouble()));
    }
}
