package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.search.FoundPlace;
import com.solvelify.routeplanner.search.PlaceSearch;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Place lookup for the map screen, so a stop can be typed instead of hunted for: a street
 * address, or the name of a business.
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

    private final PlaceSearch placeSearch;

    public PlaceSearchController(PlaceSearch placeSearch) {
        this.placeSearch = placeSearch;
    }

    @GetMapping("/search")
    public List<PlaceSearchResult> search(
            @RequestParam @NotBlank String q,
            @RequestParam(defaultValue = "8") @Min(1) @Max(25) int limit) {

        return placeSearch.search(q, limit).stream()
                .map(PlaceSearchResult::from)
                .toList();
    }

    /**
     * @param kind   "address" or "poi", a named place such as a shop, a school or a hospital
     * @param detail where a named place is, to tell two with the same name apart. Null for
     *               an address
     */
    public record PlaceSearchResult(String kind, String name, String detail, double latitude, double longitude) {

        static PlaceSearchResult from(FoundPlace place) {
            return new PlaceSearchResult(
                    place.kind().name().toLowerCase(Locale.ROOT),
                    place.name(),
                    place.detail(),
                    place.latitude(),
                    place.longitude());
        }
    }
}
