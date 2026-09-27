package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.address.AddressSearch;
import com.solvelify.routeplanner.planning.Location;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Address lookup for the map screen, so a stop can be typed instead of hunted for.
 *
 * <p>A GET, unlike the optimize endpoint: the query is short, it changes nothing, and the
 * browser and any proxy in between are then free to cache repeated keystrokes.
 *
 * <p>No {@code @Validated} on the class. Spring validates constrained parameters on its own
 * since Framework 6.1, and the failure then arrives as an exception the web layer knows how to
 * turn into a 400. Adding {@code @Validated} switches to the older proxy mechanism, whose
 * exception nothing handles, so every bad query string becomes a 500.
 */
@RestController
@RequestMapping("/api/v1/places")
public class PlaceSearchController {

    private final AddressSearch addressSearch;

    public PlaceSearchController(AddressSearch addressSearch) {
        this.addressSearch = addressSearch;
    }

    @GetMapping("/search")
    public List<PlaceSearchResult> search(
            @RequestParam @NotBlank String q,
            @RequestParam(defaultValue = "8") @Min(1) @Max(25) int limit) {

        return addressSearch.search(q, limit).stream()
                .map(PlaceSearchResult::from)
                .toList();
    }

    public record PlaceSearchResult(String name, double latitude, double longitude) {

        static PlaceSearchResult from(Location location) {
            return new PlaceSearchResult(location.name(), location.latitude(), location.longitude());
        }
    }
}
