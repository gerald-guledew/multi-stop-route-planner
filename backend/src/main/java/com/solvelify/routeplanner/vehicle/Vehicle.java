package com.solvelify.routeplanner.vehicle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The vehicle the planner works out fuel for: how much it uses, as its dashboard shows, and
 * how big it is. Petrol cars only for now.
 *
 * <p>One row at most, because there is one driver. So the id is not handed out by the
 * database. It is always the same, and saving a vehicle replaces the one there was.
 */
@Entity
@Table(name = "vehicle")
public class Vehicle {

    /** The id of the one row. */
    static final short THE_ONE = 1;

    /**
     * New Zealand's vehicle register lists a 2020 hybrid hatchback at 3.4 L/100 km. Below 2 is
     * a plug-in hybrid, which does not run on petrol alone.
     */
    public static final double LEAST_LITRES_PER_100_KM = 2;

    /** Well beyond a large petrol car in town. A figure above it is a mistake. */
    public static final double MOST_LITRES_PER_100_KM = 30;

    @Id
    private short id = THE_ONE;

    @Column(name = "litres_per_100_km")
    private double litresPer100Km;

    @Enumerated(EnumType.STRING)
    private VehicleSize size;

    /** For JPA, which makes an empty one and then fills it in from the row. */
    protected Vehicle() {
    }

    public Vehicle(double litresPer100Km, VehicleSize size) {
        // Written as "not inside the range" rather than "outside it", so NaN is refused too.
        if (!(litresPer100Km >= LEAST_LITRES_PER_100_KM && litresPer100Km <= MOST_LITRES_PER_100_KM)) {
            throw new IllegalArgumentException("litresPer100Km must be between " + LEAST_LITRES_PER_100_KM
                    + " and " + MOST_LITRES_PER_100_KM + ", was " + litresPer100Km);
        }
        if (size == null) {
            throw new IllegalArgumentException("size is needed");
        }
        this.litresPer100Km = litresPer100Km;
        this.size = size;
    }

    public double getLitresPer100Km() {
        return litresPer100Km;
    }

    public VehicleSize getSize() {
        return size;
    }
}
