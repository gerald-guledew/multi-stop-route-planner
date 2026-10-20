package com.solvelify.routeplanner.distance;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphhopper.util.PointList;
import org.junit.jupiter.api.Test;

/** How much a stretch of road climbs, the figure the roads that climb less are weighed by. */
class ClimbCountedAsRoadTest {

    /** A stretch 10 m up, 5 m down, then 15 m up. */
    private static PointList upDownAndUp() {
        PointList points = new PointList(4, true);
        points.add(-36.90, 174.70, 0);
        points.add(-36.90, 174.71, 10);
        points.add(-36.90, 174.72, 5);
        points.add(-36.90, 174.73, 20);
        return points;
    }

    @Test
    void addsUpEveryRiseAlongTheWay() {
        assertThat(ClimbCountedAsRoad.metresClimbed(upDownAndUp(), false)).isEqualTo(25);
    }

    @Test
    void climbsWhatComesDownWhenDrivenTheOtherWay() {
        assertThat(ClimbCountedAsRoad.metresClimbed(upDownAndUp(), true)).isEqualTo(5);
    }

    @Test
    void climbsNothingOnALevelStretch() {
        PointList level = new PointList(2, true);
        level.add(-36.90, 174.70, 30);
        level.add(-36.90, 174.71, 30);

        assertThat(ClimbCountedAsRoad.metresClimbed(level, false)).isZero();
        assertThat(ClimbCountedAsRoad.metresClimbed(level, true)).isZero();
    }

    @Test
    void climbsNothingWhereTheHeightsAreNotKnown() {
        PointList flat = new PointList(2, false);
        flat.add(-36.90, 174.70);
        flat.add(-36.90, 174.71);

        assertThat(ClimbCountedAsRoad.metresClimbed(flat, false)).isZero();
    }
}
