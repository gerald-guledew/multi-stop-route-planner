package com.solvelify.routeplanner.address;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One New Zealand address, as published by Toitū Te Whenua LINZ under CC BY 4.0.
 *
 * <p>The id is LINZ's own address id rather than a generated one, so re-importing updates rows
 * instead of duplicating them. The table also has a generated PostGIS `location` column, which
 * is deliberately not mapped here: nothing in Java needs it yet, and leaving it out keeps
 * Hibernate Spatial off the dependency list.
 */
@Entity
@Table(name = "address")
public class AddressEntity {

    @Id
    private Long id;

    @Column(name = "full_address", nullable = false)
    private String fullAddress;

    private String suburb;

    @Column(name = "town_city")
    private String townCity;

    private double latitude;

    private double longitude;

    protected AddressEntity() {
        // for JPA
    }

    public Long getId() {
        return id;
    }

    public String getFullAddress() {
        return fullAddress;
    }

    public String getSuburb() {
        return suburb;
    }

    public String getTownCity() {
        return townCity;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}
