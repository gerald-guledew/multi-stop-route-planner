package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.planning.Location;
import com.solvelify.routeplanner.planning.RoutePlan;
import com.solvelify.routeplanner.planning.RoutePlanningService;
import jakarta.validation.Valid;
import java.util.List;
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

        RoutePlan plan = routePlanningService.plan(
                toLocation(request.start()),
                stops,
                request.returnToStartOrDefault());

        return OptimizeRouteResponse.from(plan);
    }

    private static Location toLocation(PlaceRequest place) {
        return new Location(place.name(), place.latitude(), place.longitude());
    }
}
