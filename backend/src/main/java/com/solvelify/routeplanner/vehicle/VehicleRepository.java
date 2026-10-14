package com.solvelify.routeplanner.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and writes the vehicle. Spring Data supplies the class behind it. */
public interface VehicleRepository extends JpaRepository<Vehicle, Short> {
}
