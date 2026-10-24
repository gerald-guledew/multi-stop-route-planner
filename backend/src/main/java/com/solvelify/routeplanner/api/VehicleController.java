package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.cars.Car;
import com.solvelify.routeplanner.planning.FuelUse;
import com.solvelify.routeplanner.vehicle.Vehicle;
import com.solvelify.routeplanner.vehicle.VehicleSize;
import com.solvelify.routeplanner.vehicle.Vehicles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.OptionalDouble;
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
                .map(vehicle -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responseFor(vehicle)))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }

    @PutMapping
    public VehicleResponse save(@Valid @RequestBody VehicleRequest request) {
        Vehicle saved = request.car() == null
                ? vehicles.save(request.litresPer100Km(), VehicleSize.valueOf(request.size().toUpperCase(Locale.ROOT)))
                : vehicles.saveFromRegister(request.car().toCar(), request.litresPer100Km());
        return responseFor(saved);
    }

    private VehicleResponse responseFor(Vehicle vehicle) {
        OptionalDouble townOverOpenRoad = vehicles.townOverOpenRoadOf(vehicle);
        FuelUse fuel = townOverOpenRoad.isPresent()
                ? FuelUse.withTownOverOpenRoad(
                        vehicle.getLitresPer100Km(), townOverOpenRoad.getAsDouble(), OptionalDouble.empty())
                : new FuelUse(vehicle.getLitresPer100Km());
        return VehicleResponse.from(vehicle, fuel, vehicles.weightOf(vehicle));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove() {
        vehicles.remove();
    }

    /**
     * A vehicle entered by hand, with a size and its dashboard's figure, or a car found in the
     * register, with the dashboard's figure if the driver knows it. Boxed, so a missing figure
     * is told apart from nought.
     *
     * @param litresPer100Km what the dashboard shows, from 2 to 30
     * @param size           small, medium or large, for a vehicle entered by hand
     * @param car            the car found in the register
     */
    public record VehicleRequest(

            @DecimalMin(value = "" + Vehicle.LEAST_LITRES_PER_100_KM, message = "must be between 2 and 30")
            @DecimalMax(value = "" + Vehicle.MOST_LITRES_PER_100_KM, message = "must be between 2 and 30")
            Double litresPer100Km,

            @Pattern(regexp = "small|medium|large", message = "must be small, medium or large")
            String size,

            @Valid
            CarRequest car) {

        /** Reported as the field "sizeOrCar". */
        @AssertTrue(message = "must be a size or a car, one of the two")
        public boolean isSizeOrCar() {
            return (size == null) != (car == null);
        }

        /** Reported as the field "litresPer100KmWithSize". A size says nothing about fuel. */
        @AssertTrue(message = "litresPer100Km is needed with a size")
        public boolean isLitresPer100KmWithSize() {
            return size == null || litresPer100Km != null;
        }
    }

    /**
     * A car as the register describes it, copied from what {@code /api/v1/cars/variants} answered.
     *
     * @param engineLitres such as 1.2
     */
    public record CarRequest(
            @NotBlank String make,
            @NotBlank String model,
            @NotNull @Min(1900) @Max(2100) Integer year,
            @NotNull @DecimalMin("0.5") @DecimalMax("9.9") BigDecimal engineLitres) {

        Car toCar() {
            return new Car(make, model, year, engineLitres);
        }
    }

    /**
     * @param figureFrom             dashboard, or register for the register's laboratory figure
     * @param townLitresPer100Km     what town roads take, to a tenth: the figure split as the
     *                               register splits this car's, or the usual way when it does not
     * @param openRoadLitresPer100Km what the open road takes, to a tenth, split the same way
     * @param size                   null for a car found in the register
     * @param car                    null for a vehicle entered by hand
     * @param kilograms              what it weighs empty, to the nearest 10 kg: the register's
     *                               weight or the size's. Null when the register no longer lists
     *                               the car
     */
    public record VehicleResponse(
            double litresPer100Km,
            String figureFrom,
            double townLitresPer100Km,
            double openRoadLitresPer100Km,
            String size,
            Car car,
            Integer kilograms) {

        static VehicleResponse from(Vehicle vehicle, FuelUse fuel, OptionalDouble kilograms) {
            return new VehicleResponse(
                    vehicle.getLitresPer100Km(),
                    vehicle.getFigureFrom().name().toLowerCase(Locale.ROOT),
                    toATenth(fuel.townLitresPer100Km()),
                    toATenth(fuel.openRoadLitresPer100Km()),
                    vehicle.getSize() == null ? null : vehicle.getSize().name().toLowerCase(Locale.ROOT),
                    vehicle.getCar().orElse(null),
                    kilograms.isPresent() ? (int) Math.round(kilograms.getAsDouble() / 10) * 10 : null);
        }

        private static double toATenth(double litresPer100Km) {
            return Math.round(litresPer100Km * 10) / 10.0;
        }
    }
}
