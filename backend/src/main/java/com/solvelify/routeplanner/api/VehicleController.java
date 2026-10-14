package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.vehicle.Vehicle;
import com.solvelify.routeplanner.vehicle.VehicleSize;
import com.solvelify.routeplanner.vehicle.Vehicles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The vehicle saved in the planner, so it is entered once and not with every trip.
 *
 * <p>One vehicle, so one address and no id. A PUT saves it in place of the one there was.
 * Answers are marked not to be stored, so a vehicle changed on one device is not shown as it
 * was on another.
 */
@RestController
@RequestMapping("/api/v1/vehicle")
public class VehicleController {

    private final Vehicles vehicles;

    public VehicleController(Vehicles vehicles) {
        this.vehicles = vehicles;
    }

    /** A 204 and no body until a vehicle is saved. Having none is not an error. */
    @GetMapping
    public ResponseEntity<VehicleResponse> current() {
        return vehicles.current()
                .map(vehicle -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(VehicleResponse.from(vehicle)))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }

    @PutMapping
    public VehicleResponse save(@Valid @RequestBody VehicleRequest request) {
        VehicleSize size = VehicleSize.valueOf(request.size().toUpperCase(Locale.ROOT));
        return VehicleResponse.from(vehicles.save(request.litresPer100Km(), size));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove() {
        vehicles.remove();
    }

    /**
     * Boxed, so a missing figure is refused rather than read as nought.
     *
     * @param litresPer100Km what the dashboard shows, from 2 to 30
     * @param size           small, medium or large
     */
    public record VehicleRequest(

            @NotNull
            @DecimalMin(value = "" + Vehicle.LEAST_LITRES_PER_100_KM, message = "must be between 2 and 30")
            @DecimalMax(value = "" + Vehicle.MOST_LITRES_PER_100_KM, message = "must be between 2 and 30")
            Double litresPer100Km,

            @NotNull
            @Pattern(regexp = "small|medium|large", message = "must be small, medium or large")
            String size) {
    }

    public record VehicleResponse(double litresPer100Km, String size) {

        static VehicleResponse from(Vehicle vehicle) {
            return new VehicleResponse(vehicle.getLitresPer100Km(), vehicle.getSize().name().toLowerCase(Locale.ROOT));
        }
    }
}
