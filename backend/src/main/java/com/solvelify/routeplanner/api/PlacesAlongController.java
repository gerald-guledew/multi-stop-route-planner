package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.along.PlaceAlong;
import com.solvelify.routeplanner.along.PlacesAlongTheRoute;
import com.solvelify.routeplanner.planning.Location;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Places to stop at on the way, typed by name: for the "+" between two stops of a planned trip,
 * and for finding along the whole of it.
 *
 * <p>A POST, unlike the search. The route is too long for a URL, and a URL is what logs keep,
 * which is no place for the exact spots of somebody's trip.
 */
@RestController
@RequestMapping("/api/v1/places")
public class PlacesAlongController {

    /** As many as the search gives, when the caller does not say. */
    private static final int USUAL_LIMIT = 8;

    private final PlacesAlongTheRoute placesAlongTheRoute;

    public PlacesAlongController(PlacesAlongTheRoute placesAlongTheRoute) {
        this.placesAlongTheRoute = placesAlongTheRoute;
    }

    @PostMapping("/along")
    public List<PlaceAlongResult> along(@Valid @RequestBody AlongRequest request) {
        List<Location> route = request.route().stream()
                .map(place -> new Location(place.name(), place.latitude(), place.longitude()))
                .toList();
        OptionalInt leg = request.leg() == null ? OptionalInt.empty() : OptionalInt.of(request.leg());
        int limit = request.limit() == null ? USUAL_LIMIT : request.limit();

        return placesAlongTheRoute.find(request.q(), route, leg, limit).stream()
                .map(PlaceAlongResult::from)
                .toList();
    }

    /**
     * @param q     what was typed: an address, or the name of a business
     * @param route the places of the trip in the order its plan drives to them: the start first,
     *              and the start again last on a round trip. Ten stops is twelve places at most
     * @param leg   the one leg to look along, counting from 0: from the place at that turn to the
     *              next. Left out, the whole route is looked along
     * @param limit how many places to give back. Left out, 8
     */
    public record AlongRequest(

            @NotBlank
            @Size(max = 100, message = "must be at most 100 characters")
            String q,

            @NotNull
            @Size(min = 2, max = 12, message = "must hold from 2 to 12 places")
            List<@Valid PlaceRequest> route,

            @Min(value = 0, message = "must be 0 or more")
            Integer leg,

            @Min(value = 1, message = "must be between 1 and 25")
            @Max(value = 25, message = "must be between 1 and 25")
            Integer limit) {

        /** Reported as the field "legOnTheRoute". A route of n places has n - 1 legs. */
        @AssertTrue(message = "must be one of the route's legs, counting from 0")
        public boolean isLegOnTheRoute() {
            return leg == null || route == null || leg < route.size() - 1;
        }
    }

    /**
     * @param kind         "address" or "poi", a named place such as a shop
     * @param detail       where a named place is, to tell two with the same name apart. Null for
     *                     an address
     * @param leg          the leg it is a stop on, counting from 0
     * @param kmAdded      how much further that leg is with the stop on it
     * @param secondsAdded how much longer that leg takes with the stop on it, on clear roads and
     *                     with no time spent there. Null on straight lines
     */
    public record PlaceAlongResult(
            String kind,
            String name,
            String detail,
            double latitude,
            double longitude,
            int leg,
            double kmAdded,
            Long secondsAdded) {

        static PlaceAlongResult from(PlaceAlong along) {
            return new PlaceAlongResult(
                    along.place().kind().name().toLowerCase(Locale.ROOT),
                    along.place().name(),
                    along.place().detail(),
                    along.place().latitude(),
                    along.place().longitude(),
                    along.leg(),
                    Math.round(along.kmAdded() * 100) / 100.0,
                    along.secondsAdded().isPresent() ? Math.round(along.secondsAdded().getAsDouble()) : null);
        }
    }
}
