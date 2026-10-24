package com.solvelify.routeplanner.vehicle;

import com.solvelify.routeplanner.cars.Car;
import com.solvelify.routeplanner.cars.CarNotInRegisterException;
import com.solvelify.routeplanner.cars.CarRegister;
import com.solvelify.routeplanner.cars.CarVariant;
import java.util.Optional;
import java.util.OptionalDouble;
import org.springframework.stereotype.Service;

/** The vehicle saved in the planner: reading it, replacing it, removing it. */
@Service
public class Vehicles {

    private final VehicleRepository repository;
    private final CarRegister register;

    public Vehicles(VehicleRepository repository, CarRegister register) {
        this.repository = repository;
        this.register = register;
    }

    /** Empty until a vehicle is saved. */
    public Optional<Vehicle> current() {
        return repository.findById(Vehicle.THE_ONE);
    }

    /**
     * Saves a vehicle entered by hand, in place of the one there was, if any. The id is always
     * the same, so JPA reads the row first and then inserts or updates it.
     */
    public Vehicle save(double litresPer100Km, VehicleSize size) {
        return repository.saveAndFlush(new Vehicle(litresPer100Km, size));
    }

    /**
     * Saves a car found in NZTA's register, in place of the vehicle there was, if any.
     *
     * @param dashboardLitresPer100Km what its dashboard shows, or null to go by the register's figure
     * @throws CarNotInRegisterException when the register does not list the car
     */
    public Vehicle saveFromRegister(Car car, Double dashboardLitresPer100Km) {
        CarVariant found = register.find(car).orElseThrow(() -> new CarNotInRegisterException(car));
        return repository.saveAndFlush(new Vehicle(found, dashboardLitresPer100Km));
    }

    /**
     * What the vehicle weighs empty: the register's weight for a car found there, the size's for
     * one entered by hand. Empty when the register no longer lists the car.
     */
    public OptionalDouble weightOf(Vehicle vehicle) {
        return vehicle.getCar()
                .map(register::weightOf)
                .orElseGet(() -> OptionalDouble.of(vehicle.getSize().kilograms()));
    }

    /**
     * How many times the open road's fuel the vehicle takes in town, by the register's figures for
     * a car found there. Empty for a vehicle entered by hand, and for a car the register gives no
     * such figures, which then go the usual way.
     */
    public OptionalDouble townOverOpenRoadOf(Vehicle vehicle) {
        return vehicle.getCar()
                .map(register::townOverOpenRoadOf)
                .orElse(OptionalDouble.empty());
    }

    /** Nothing happens when no vehicle is saved: removing nothing is not a mistake. */
    public void remove() {
        repository.deleteById(Vehicle.THE_ONE);
    }
}
