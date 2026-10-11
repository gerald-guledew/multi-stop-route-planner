package com.solvelify.routeplanner.saved;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes saved places. There is no class behind this interface to write: Spring Data
 * supplies one when the application starts, with the usual save, find and delete methods.
 */
public interface SavedPlaceRepository extends JpaRepository<SavedPlace, Long> {
}
