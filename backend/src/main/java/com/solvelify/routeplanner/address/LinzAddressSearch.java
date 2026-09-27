package com.solvelify.routeplanner.address;

import com.solvelify.routeplanner.planning.Location;
import java.util.List;
import org.springframework.stereotype.Service;

/** Address search over the LINZ data held in PostgreSQL. */
@Service
public class LinzAddressSearch implements AddressSearch {

    private final AddressRepository addresses;

    public LinzAddressSearch(AddressRepository addresses) {
        this.addresses = addresses;
    }

    @Override
    public List<Location> search(String term, int limit) {
        String trimmed = term.trim();
        if (trimmed.length() < 3) {
            // Two characters match half the country and teach the user nothing.
            return List.of();
        }

        return addresses.search(trimmed, limit).stream()
                .map(address -> new Location(
                        address.getFullAddress(), address.getLatitude(), address.getLongitude()))
                .toList();
    }
}
