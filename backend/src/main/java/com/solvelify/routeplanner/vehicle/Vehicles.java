package com.solvelify.routeplanner.vehicle;

import java.util.Optional;
import org.springframework.stereotype.Service;

/** The vehicle saved in the planner: reading it, replacing it, removing it. */
@Service
public class Vehicles {

    private final VehicleRepository repository;

    public Vehicles(VehicleRepository repository) {
        this.repository = repository;
    }

    /** Empty until a vehicle is saved. */
    public Optional<Vehicle> current() {
        return repository.findById(Vehicle.THE_ONE);
    }

    /**
     * Saves this vehicle in place of the one there was, if any. The id is always the same, so
     * JPA reads the row first and then inserts or updates it.
     */
    public Vehicle save(double litresPer100Km, VehicleSize size) {
        return repository.saveAndFlush(new Vehicle(litresPer100Km, size));
    }

    /** Nothing happens when no vehicle is saved: removing nothing is not a mistake. */
    public void remove() {
        repository.deleteById(Vehicle.THE_ONE);
    }
}
