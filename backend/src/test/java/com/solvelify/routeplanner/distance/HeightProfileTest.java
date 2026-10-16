package com.solvelify.routeplanner.distance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.solvelify.routeplanner.planning.ClimbAndDescent;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The rule that turns the heights along a drive into a climb and a descent.
 *
 * <p>Every road here is made up, with a point every 30 m unless a test says otherwise, which
 * is about what the real map gives. What the rule makes of real roads is in
 * {@code RealHeightsTest}, which needs the height files.
 */
class HeightProfileTest {

    private static final double A_HAIR = 1e-6;

    // Whole roads.

    @Test
    void neitherClimbsNorDescendsOnALevelRoad() {
        Road level = level(30, 3000);

        assertThat(level.climbAndDescent()).isEqualTo(ClimbAndDescent.LEVEL);
    }

    @Test
    void climbsExactlyItsRiseOnAnEvenSlope() {
        Road slope = Road.from(20).to(320, 3000);

        ClimbAndDescent counted = slope.climbAndDescent();

        assertThat(counted.climbMetres()).isCloseTo(300, within(A_HAIR));
        assertThat(counted.descentMetres()).isCloseTo(0, within(A_HAIR));
    }

    @Test
    void descendsExactlyItsFallOnAnEvenSlope() {
        Road slope = Road.from(320).to(20, 3000);

        ClimbAndDescent counted = slope.climbAndDescent();

        assertThat(counted.climbMetres()).isCloseTo(0, within(A_HAIR));
        assertThat(counted.descentMetres()).isCloseTo(300, within(A_HAIR));
    }

    @Test
    void countsAHillInFullOnBothSides() {
        // Up 80 m, 300 m of level road along the top, then down 50 m.
        Road overTheHill = Road.from(0).to(80, 1000).to(80, 300).to(30, 1000);

        ClimbAndDescent counted = overTheHill.climbAndDescent();

        // The far end is only 30 m higher than the near one.
        assertThat(counted.climbMetres()).isCloseTo(80, within(A_HAIR));
        assertThat(counted.descentMetres()).isCloseTo(50, within(A_HAIR));
    }

    @Test
    void keepsEachDescentWithTheRoadItFallsOver() {
        // Down 50 m over 1 km, level for 1 km, then down 30 m over 300 m.
        Road twoDescents = Road.from(100).to(50, 1000).to(50, 1000).to(20, 300);

        ClimbAndDescent counted = twoDescents.climbAndDescent();

        // One descent from the top to the bottom: the level stretch is no turn.
        assertThat(counted.descents()).singleElement().satisfies(descent -> {
            assertThat(descent.dropMetres()).isCloseTo(80, within(A_HAIR));
            assertThat(descent.overMetres()).isCloseTo(2300, within(A_HAIR));
        });
    }

    @Test
    void keepsTheDescentsOfAHillAndADipApart() {
        // Up 60 m and down again over a hill, then down 40 m into a dip and back up.
        Road hillAndDip = Road.from(0).to(60, 1500).to(0, 1500).to(-40, 1500).to(0, 1500);

        ClimbAndDescent counted = hillAndDip.climbAndDescent();

        assertThat(counted.descents()).singleElement().satisfies(descent -> {
            // From the top of the hill to the bottom of the dip, evened out a little at both.
            assertThat(descent.dropMetres()).isCloseTo(counted.descentMetres(), within(A_HAIR));
            assertThat(descent.overMetres()).isCloseTo(3000, within(150.0));
        });
    }

    @Test
    void takesALittleOffATopThatComesToAPoint() {
        // No road has a top like this one. The evening out rounds it, and 2 m of it go.
        Road sharp = Road.from(0).to(80, 1000).to(30, 1000);

        ClimbAndDescent counted = sharp.climbAndDescent();

        assertThat(counted.climbMetres()).isBetween(77.0, 79.0);
        assertThat(counted.descentMetres()).isBetween(47.0, 49.0);
    }

    // What is passed over.

    @Test
    void passesOverTheWobbleOfTheGridOnALevelRoad() {
        // A metre up, a metre down, every 30 m for 3 km. Added up as it stands: 99 m of climbing.
        Road wobbling = level(30, 3000).wobbling(1);

        assertThat(plainSumOf(wobbling).climbMetres()).isCloseTo(99, within(A_HAIR));
        assertThat(wobbling.climbAndDescent()).isEqualTo(ClimbAndDescent.LEVEL);
    }

    @Test
    void doesNotCountTheWobbleAsDescentOnTheWayUpAHill() {
        Road wobblingUp = Road.from(0).to(300, 3000).wobbling(3);

        ClimbAndDescent counted = wobblingUp.climbAndDescent();

        assertThat(plainSumOf(wobblingUp).descentMetres()).isGreaterThan(50);
        assertThat(counted.climbMetres()).isCloseTo(300, within(A_HAIR));
        assertThat(counted.descentMetres()).isCloseTo(0, within(A_HAIR));
    }

    @Test
    void doesNotMakeAHillOfOneWrongHeight() {
        // One point read 20 m too high, off the bank above the road.
        Road oneBadPoint = level(30, 3000).withPointRaised(50, 20);

        assertThat(oneBadPoint.climbAndDescent()).isEqualTo(ClimbAndDescent.LEVEL);
    }

    @Test
    void passesOverARiseOfSixMetres() {
        Road bump = Road.from(30).to(30, 1000).to(36, 300).to(30, 300).to(30, 1000);

        assertThat(bump.climbAndDescent()).isEqualTo(ClimbAndDescent.LEVEL);
    }

    @Test
    void countsARiseOfFifteenMetres() {
        Road rise = Road.from(30).to(30, 1000).to(45, 300).to(45, 300).to(30, 300).to(30, 1000);

        ClimbAndDescent counted = rise.climbAndDescent();

        assertThat(counted.climbMetres()).isCloseTo(15, within(A_HAIR));
        assertThat(counted.descentMetres()).isCloseTo(15, within(A_HAIR));
    }

    @Test
    void countsADipOnTheWayUpWhenItIsDeepEnough() {
        // Up 100 m, down 20 m into a gully, and up again to 200 m.
        Road withAGully = Road.from(0).to(100, 1500).to(100, 200).to(80, 400).to(80, 200).to(200, 1500);

        ClimbAndDescent counted = withAGully.climbAndDescent();

        assertThat(counted.climbMetres()).isCloseTo(220, within(A_HAIR));
        assertThat(counted.descentMetres()).isCloseTo(20, within(A_HAIR));
    }

    @Test
    void keepsMostOfAShortSteepHill() {
        // 40 m high and 600 m from foot to foot. Evened out over 150 m it keeps seven eighths.
        Road shortHill = Road.from(10).to(10, 500).to(50, 300).to(10, 300).to(10, 500);

        ClimbAndDescent counted = shortHill.climbAndDescent();

        assertThat(counted.climbMetres()).isCloseTo(35, within(0.01));
        assertThat(counted.descentMetres()).isCloseTo(35, within(0.01));
    }

    // What always holds.

    @Test
    void makesTheClimbLessTheDescentTheHeightBetweenTheEnds() {
        Random anyRoad = new Random(20261014);
        for (int tried = 0; tried < 500; tried++) {
            Road rough = roughRoad(anyRoad);

            ClimbAndDescent counted = rough.climbAndDescent();

            assertThat(counted.climbMetres() - counted.descentMetres())
                    .as("road %d", tried)
                    .isCloseTo(rough.lastHeight() - rough.firstHeight(), within(A_HAIR));
        }
    }

    @Test
    void givesTheSameTwoFiguresWithTheirPlacesChangedForTheDriveBack() {
        Random anyRoad = new Random(1076);
        for (int tried = 0; tried < 500; tried++) {
            Road rough = roughRoad(anyRoad);

            ClimbAndDescent there = rough.climbAndDescent();
            ClimbAndDescent back = rough.drivenTheOtherWay().climbAndDescent();

            assertThat(back.climbMetres()).as("road %d", tried).isCloseTo(there.descentMetres(), within(A_HAIR));
            assertThat(back.descentMetres()).as("road %d", tried).isCloseTo(there.climbMetres(), within(A_HAIR));
        }
    }

    @Test
    void neverCountsMoreThanAddingUpEveryChangeWould() {
        Random anyRoad = new Random(555);
        for (int tried = 0; tried < 500; tried++) {
            Road rough = roughRoad(anyRoad);

            assertThat(rough.climbAndDescent().climbMetres())
                    .as("road %d", tried)
                    .isLessThanOrEqualTo(plainSumOf(rough).climbMetres() + A_HAIR);
        }
    }

    // Evening out.

    @Test
    void leavesTheTwoEndsAtTheHeightTheyCameWith() {
        Road rough = roughRoad(new Random(7));

        double[] evened = HeightProfile.evenedOut(rough.metresAlong, rough.heights, 150);

        assertThat(evened[0]).isEqualTo(rough.firstHeight());
        assertThat(evened[evened.length - 1]).isEqualTo(rough.lastHeight());
    }

    @Test
    void leavesAnEvenSlopeExactlyAsItIs() {
        Road slope = Road.from(20).to(320, 3000);

        double[] evened = HeightProfile.evenedOut(slope.metresAlong, slope.heights, 150);

        for (int at = 0; at < evened.length; at++) {
            assertThat(evened[at]).as("point %d", at).isCloseTo(slope.heights[at], within(A_HAIR));
        }
    }

    @Test
    void spreadsOneWrongHeightThinlyOverTheRoadAroundIt() {
        Road oneBadPoint = level(30, 3000).withPointRaised(50, 20);

        double[] evened = HeightProfile.evenedOut(oneBadPoint.metresAlong, oneBadPoint.heights, 150);

        // 20 m too high for 30 m either side of one point, spread over 150 m of road: 4 m.
        assertThat(evened[50]).isCloseTo(34, within(A_HAIR));
    }

    @Test
    void averagesOverRoadAndNotOverPoints() {
        // The same hill twice. The second time its west side is described by ten times the points.
        Road evenlySpaced = Road.from(0).to(80, 900).to(80, 300).to(30, 900);
        Road crowdedOnOneSide = Road.from(0).every(3).to(80, 900).every(30).to(80, 300).to(30, 900);

        assertThat(crowdedOnOneSide.climbAndDescent().climbMetres())
                .isCloseTo(evenlySpaced.climbAndDescent().climbMetres(), within(A_HAIR));
        assertThat(crowdedOnOneSide.climbAndDescent().descentMetres())
                .isCloseTo(evenlySpaced.climbAndDescent().descentMetres(), within(A_HAIR));
    }

    @Test
    void isNotThrownByTheSamePointGivenTwice() {
        // The top of a hill, given twice. Between the two is a stretch with no length to it.
        double[] metresAlong = {0, 500, 500, 1000};
        double[] heights = {0, 50, 50, 20};

        ClimbAndDescent counted = HeightProfile.climbAndDescent(metresAlong, heights);

        // 3 m off the pointed top, as for any other.
        assertThat(counted.climbMetres()).isCloseTo(47, within(A_HAIR));
        assertThat(counted.descentMetres()).isCloseTo(27, within(A_HAIR));
    }

    // Counting turns, on heights taken as they are.

    @Test
    void countsATurnOfExactlyTheLeast() {
        assertThat(HeightProfile.counted(new double[] {0, 10, 0}, 10)).isEqualTo(new ClimbAndDescent(10, 10));
        assertThat(HeightProfile.counted(new double[] {10, 0, 10}, 10)).isEqualTo(new ClimbAndDescent(10, 10));
    }

    @Test
    void passesOverATurnJustUnderTheLeast() {
        assertThat(HeightProfile.counted(new double[] {0, 9.99, 0}, 10)).isEqualTo(ClimbAndDescent.LEVEL);
        assertThat(HeightProfile.counted(new double[] {9.99, 0, 9.99}, 10)).isEqualTo(ClimbAndDescent.LEVEL);
    }

    @Test
    void countsAClimbFromTheLastTurnAndNotFromTheDipItPassedOver() {
        // Up to 12, back to 4, on to 20. The dip of 8 m is no turn, so this is one climb of 20.
        assertThat(HeightProfile.counted(new double[] {0, 12, 4, 20}, 10)).isEqualTo(new ClimbAndDescent(20, 0));
    }

    @Test
    void countsRollingGroundThatNeverGetsFarFromWhereItStarted() {
        // Eight below the start, eight above, and again. No point is 10 m from the start, yet
        // the road swings by 16 m each time, and those swings have to count.
        double[] rolling = {0, -8, 8, -8, 8, -8, 0};

        ClimbAndDescent counted = HeightProfile.counted(rolling, 10);

        // Added up as it stands this is 40 m each way. The first 8 m down and the last 8 m up
        // are each under the least and are passed over, which takes 16 m off both.
        assertThat(counted.climbMetres()).isEqualTo(24);
        assertThat(counted.descentMetres()).isEqualTo(24);
    }

    @Test
    void countsWhatIsLeftAtTheEndHoweverSmall() {
        // Up 50 and then 4 back down to the end. The 4 m is under the least, and it is where
        // the drive ends, so it has to be in the answer.
        ClimbAndDescent counted = HeightProfile.counted(new double[] {0, 50, 46}, 10);

        assertThat(counted.climbMetres() - counted.descentMetres()).isEqualTo(46);
    }

    @Test
    void countsOnlyTheHeightBetweenTheEndsWhenTheRoadNeverTurns() {
        assertThat(HeightProfile.counted(new double[] {30, 33, 29, 36}, 10)).isEqualTo(new ClimbAndDescent(6, 0));
        assertThat(HeightProfile.counted(new double[] {36, 29, 33, 30}, 10)).isEqualTo(new ClimbAndDescent(0, 6));
    }

    // Roads too short to say anything about.

    @Test
    void saysLevelOfADriveWithNoLengthToIt() {
        assertThat(HeightProfile.climbAndDescent(new double[] {}, new double[] {})).isEqualTo(ClimbAndDescent.LEVEL);
        assertThat(HeightProfile.climbAndDescent(new double[] {0}, new double[] {42})).isEqualTo(ClimbAndDescent.LEVEL);
    }

    @Test
    void takesTwoPointsAsTheSlopeBetweenThem() {
        assertThat(HeightProfile.climbAndDescent(new double[] {0, 400}, new double[] {10, 25}))
                .isEqualTo(new ClimbAndDescent(15, 0));
    }

    @Test
    void refusesHeightsForADifferentNumberOfPoints() {
        assertThatThrownBy(() -> HeightProfile.climbAndDescent(new double[] {0, 30, 60}, new double[] {1, 2}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("2");
    }

    // A made-up road, built a stretch at a time.

    private static Road level(double height, double forMetres) {
        return Road.from(height).to(height, forMetres);
    }

    private static Road roughRoad(Random random) {
        Road road = Road.from(random.nextInt(200)).every(10 + random.nextInt(60));
        int stretches = 2 + random.nextInt(12);
        double height = 100;
        for (int stretch = 0; stretch < stretches; stretch++) {
            height = Math.max(0, height + random.nextInt(121) - 60);
            road = road.to(height, 100 + random.nextInt(900));
        }
        return road.wobbling(random.nextInt(6));
    }

    /** Every rise and every fall between neighbouring points, which is what the engine itself reports. */
    private static ClimbAndDescent plainSumOf(Road road) {
        return HeightProfile.counted(road.heights, 0);
    }

    private static final class Road {

        private final double[] metresAlong;
        private final double[] heights;
        private final double metresBetweenPoints;

        private Road(double[] metresAlong, double[] heights, double metresBetweenPoints) {
            this.metresAlong = metresAlong;
            this.heights = heights;
            this.metresBetweenPoints = metresBetweenPoints;
        }

        static Road from(double height) {
            return new Road(new double[] {0}, new double[] {height}, 30);
        }

        /** How far apart the points of the stretches added from here on are. */
        Road every(double metres) {
            return new Road(metresAlong, heights, metres);
        }

        /** An even slope from where the road has got to, to this height, over so many metres. */
        Road to(double height, double overMetres) {
            int steps = (int) Math.round(overMetres / metresBetweenPoints);
            int had = heights.length;
            double[] along = Arrays.copyOf(metresAlong, had + steps);
            double[] high = Arrays.copyOf(heights, had + steps);
            for (int step = 1; step <= steps; step++) {
                along[had - 1 + step] = metresAlong[had - 1] + overMetres * step / steps;
                high[had - 1 + step] = heights[had - 1] + (height - heights[had - 1]) * step / steps;
            }
            return new Road(along, high, metresBetweenPoints);
        }

        /** Up one point and down the next, by so many metres, leaving the two ends alone. */
        Road wobbling(double metres) {
            double[] wobbled = heights.clone();
            for (int at = 1; at < wobbled.length - 1; at++) {
                wobbled[at] += at % 2 == 1 ? metres : -metres;
            }
            return new Road(metresAlong, wobbled, metresBetweenPoints);
        }

        Road withPointRaised(int point, double metres) {
            double[] raised = heights.clone();
            raised[point] += metres;
            return new Road(metresAlong, raised, metresBetweenPoints);
        }

        Road drivenTheOtherWay() {
            int last = heights.length - 1;
            double[] along = new double[heights.length];
            double[] high = new double[heights.length];
            for (int at = 0; at <= last; at++) {
                along[at] = metresAlong[last] - metresAlong[last - at];
                high[at] = heights[last - at];
            }
            return new Road(along, high, metresBetweenPoints);
        }

        ClimbAndDescent climbAndDescent() {
            return HeightProfile.climbAndDescent(metresAlong, heights);
        }

        double firstHeight() {
            return heights[0];
        }

        double lastHeight() {
            return heights[heights.length - 1];
        }
    }
}
