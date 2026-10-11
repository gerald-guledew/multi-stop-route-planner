package com.solvelify.routeplanner.saved;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A place somebody named so they can pick it again without typing: home, the office, a shop
 * the map data does not know about.
 *
 * <p>The first JPA entity here. Search is plain SQL because it reads two tables that share no
 * class. This is one table, written and read a row at a time, which is what JPA is good at.
 *
 * <p>The table has a {@code created_at} column as well. The database fills it in and nothing
 * reads it yet, so it is not mapped.
 */
@Entity
@Table(name = "saved_place")
public class SavedPlace {

    /** Left empty on a new place. The database numbers the row when it is inserted. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private double latitude;

    private double longitude;

    /** For JPA, which makes an empty one and then fills it in from the row. */
    protected SavedPlace() {
    }

    public SavedPlace(String name, double latitude, double longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}
