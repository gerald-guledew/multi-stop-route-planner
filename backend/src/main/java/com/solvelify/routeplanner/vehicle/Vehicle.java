package com.solvelify.routeplanner.vehicle;

import com.solvelify.routeplanner.cars.Car;
import com.solvelify.routeplanner.cars.CarVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * The vehicle the planner works out fuel for. Petrol cars only for now. It is entered by hand,
 * with the fuel use its dashboard shows and its size, or found in NZTA's register by make,
 * model, year and engine, which gives a laboratory figure unless the dashboard's is typed too.
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
    @Column(name = "figure_from")
    private FigureFrom figureFrom;

    /** Null for a car found in the register, which says more than a size does. */
    @Enumerated(EnumType.STRING)
    private VehicleSize size;

    // The car found in the register, as it describes it. All null for a vehicle entered by hand.
    private String make;
    private String model;
    private Short year;
    @Column(name = "engine_litres")
    private BigDecimal engineLitres;

    /** For JPA, which makes an empty one and then fills it in from the row. */
    protected Vehicle() {
    }

    /** A vehicle entered by hand: the fuel use its dashboard shows, and its size. */
    public Vehicle(double litresPer100Km, VehicleSize size) {
        if (size == null) {
            throw new IllegalArgumentException("size is needed");
        }
        this.litresPer100Km = checked(litresPer100Km);
        this.figureFrom = FigureFrom.DASHBOARD;
        this.size = size;
    }

    /**
     * A car found in the register. It goes by the register's figure, unless the dashboard's is
     * given: that is what this car really uses, so it is the one kept.
     *
     * @param dashboardLitresPer100Km what its dashboard shows, or null when the driver does not know
     */
    public Vehicle(CarVariant found, Double dashboardLitresPer100Km) {
        if (dashboardLitresPer100Km == null) {
            this.litresPer100Km = checked(found.litresPer100Km());
            this.figureFrom = FigureFrom.REGISTER;
        } else {
            this.litresPer100Km = checked(dashboardLitresPer100Km);
            this.figureFrom = FigureFrom.DASHBOARD;
        }
        Car car = found.car();
        this.make = car.make();
        this.model = car.model();
        this.year = (short) car.year();
        this.engineLitres = car.engineLitres();
    }

    private static double checked(double litresPer100Km) {
        // Written as "not inside the range" rather than "outside it", so NaN is refused too.
        if (!(litresPer100Km >= LEAST_LITRES_PER_100_KM && litresPer100Km <= MOST_LITRES_PER_100_KM)) {
            throw new IllegalArgumentException("litresPer100Km must be between " + LEAST_LITRES_PER_100_KM
                    + " and " + MOST_LITRES_PER_100_KM + ", was " + litresPer100Km);
        }
        return litresPer100Km;
    }

    public double getLitresPer100Km() {
        return litresPer100Km;
    }

    public FigureFrom getFigureFrom() {
        return figureFrom;
    }

    /** Null for a car found in the register. */
    public VehicleSize getSize() {
        return size;
    }

    /** The car found in the register. Empty for a vehicle entered by hand. */
    public Optional<Car> getCar() {
        return make == null
                ? Optional.empty()
                : Optional.of(new Car(make, model, year, engineLitres));
    }
}
