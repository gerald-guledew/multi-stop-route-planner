package com.solvelify.routeplanner.distance;

import com.solvelify.routeplanner.planning.Location;
import com.solvelify.routeplanner.planning.TravelMatrix;
import com.solvelify.routeplanner.planning.TravelMatrixProvider;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Fills the travel matrix with straight-line distances.
 *
 * <p>Fast and free, but it ignores roads, water and one-way streets. Devonport to Sylvia Park
 * measures about 10 km across the harbour here, while the drive goes the long way round.
 * Step 3 replaces this with GraphHopper.
 */
@Component
public class HaversineTravelMatrixProvider implements TravelMatrixProvider {

    @Override
    public TravelMatrix matrixFor(List<Location> places) {
        int size = places.size();
        double[][] distances = new double[size][size];

        // Straight-line distance is symmetric, so only half the table needs calculating.
        for (int from = 0; from < size; from++) {
            for (int to = from + 1; to < size; to++) {
                double km = Haversine.distanceKm(places.get(from), places.get(to));
                distances[from][to] = km;
                distances[to][from] = km;
            }
        }

        return new TravelMatrix(distances);
    }
}
