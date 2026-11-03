package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.search.NearbyAddress;
import com.solvelify.routeplanner.search.PlaceSearch;
import com.solvelify.routeplanner.search.ReferencePoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The address nearest a pin. The map screen asks when a pin is put down, so that a stop reads as a
 * place and not as "Stop 3".
 *
 * <p>A POST and not a GET, so the exact position travels in the body. A URL is what logs keep, and
 * a pin is often somebody's own front door.
 */
@RestController
@RequestMapping("/api/v1/places")
public class NearestAddressController {

    /**
     * How far off an address may be and still say where a pin is. One point in ten on New Zealand's
     * roads is further from any address than this, out in the country.
     */
    private static final double WITHIN_METRES = 1000;

    private final PlaceSearch placeSearch;

    public NearestAddressController(PlaceSearch placeSearch) {
        this.placeSearch = placeSearch;
    }

    @PostMapping("/nearest-address")
    public NearestAddressResult nearest(@Valid @RequestBody NearestAddressRequest request) {
        Optional<NearbyAddress> nearest = placeSearch.nearestAddress(
                new ReferencePoint(request.latitude(), request.longitude()), WITHIN_METRES);
        return nearest
                .map(address -> new NearestAddressResult(address.name(), Math.round(address.metres())))
                .orElse(new NearestAddressResult(null, null));
    }

    /** Boxed, so a missing field is refused rather than read as nought, a real position in the Atlantic. */
    public record NearestAddressRequest(

            @NotNull
            @DecimalMin(value = "-90.0", message = "must be between -90 and 90")
            @DecimalMax(value = "90.0", message = "must be between -90 and 90")
            Double latitude,

            @NotNull
            @DecimalMin(value = "-180.0", message = "must be between -180 and 180")
            @DecimalMax(value = "180.0", message = "must be between -180 and 180")
            Double longitude) {
    }

    /**
     * @param name   the street address and its suburb. Null when no address is near enough to say
     *               where the spot is, or none has been imported
     * @param metres how far the spot is from that address, in whole metres. Null with the name
     */
    public record NearestAddressResult(String name, Long metres) {
    }
}
