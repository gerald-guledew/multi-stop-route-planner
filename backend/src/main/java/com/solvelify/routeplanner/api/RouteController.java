package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.planning.ChosenFor;
import com.solvelify.routeplanner.planning.FuelEstimate;
import com.solvelify.routeplanner.planning.FuelUse;
import com.solvelify.routeplanner.planning.Location;
import com.solvelify.routeplanner.planning.RoutePlan;
import com.solvelify.routeplanner.planning.RoutePlanningService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.function.Predicate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only HTTP entry point so far.
 *
 * <p>It validates, translates between the wire format and the domain, and hands off. No
 * arithmetic lives here, which is why the planning code can be tested without Spring.
 */
@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final RoutePlanningService routePlanningService;

    public RouteController(RoutePlanningService routePlanningService) {
        this.routePlanningService = routePlanningService;
    }

    @PostMapping("/optimize")
    public OptimizeRouteResponse optimize(@Valid @RequestBody OptimizeRouteRequest request) {
        List<Location> stops = request.stops().stream()
                .map(RouteController::toLocation)
                .toList();
        Optional<FuelUse> vehicle = Optional.ofNullable(request.vehicle())
                .map(OptimizeRouteRequest.VehicleRequest::fuelUse);

        RoutePlan plan = routePlanningService.plan(
                toLocation(request.start()),
                stops,
                positionsOf(request.stops(), PlaceRequest::keepInPlaceOrDefault),
                positionsOf(request.stops(), PlaceRequest::stopOverOrDefault),
                request.returnToStartOrDefault(),
                chosenFor(request),
                vehicle);

        Optional<FuelEstimate> fuel = vehicle.map(fuelUse -> FuelEstimate.of(plan, fuelUse));

        return OptimizeRouteResponse.from(plan, fuel);
    }

    /** The least fuel, the fastest order, or else the shortest. */
    private static ChosenFor chosenFor(OptimizeRouteRequest request) {
        if (request.leastFuelOrDefault()) {
            return ChosenFor.FUEL;
        }
        return request.fastestOrDefault() ? ChosenFor.TIME : ChosenFor.DISTANCE;
    }

    /** Where in the list the stops with a mark are, counting from 0. */
    private static Set<Integer> positionsOf(List<PlaceRequest> stops, Predicate<PlaceRequest> marked) {
        return IntStream.range(0, stops.size())
                .filter(position -> marked.test(stops.get(position)))
                .boxed()
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Location toLocation(PlaceRequest place) {
        return new Location(place.name(), place.latitude(), place.longitude());
    }
}
