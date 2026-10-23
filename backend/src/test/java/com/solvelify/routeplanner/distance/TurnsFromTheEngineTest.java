package com.solvelify.routeplanner.distance;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.RoundaboutInstruction;
import com.graphhopper.util.details.PathDetail;
import com.solvelify.routeplanner.planning.Turn;
import com.solvelify.routeplanner.planning.Turn.Way;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The engine's instructions read as turns. Each instruction below is shaped like one the engine
 * gave for a drive across Auckland: Sylvia Park to the museum, and the Sky Tower to the airport.
 */
class TurnsFromTheEngineTest {

    @Test
    void setsOffOnTheBearingTheEngineGives() {
        Turn setOff = only(instruction(Instruction.CONTINUE_ON_STREET, "", 99, Map.of("heading", 346.5)));

        assertThat(setOff.way()).isEqualTo(Way.SET_OFF);
        assertThat(setOff.heading()).hasValue(346.5);
        assertThat(setOff.street()).isEmpty();
        assertThat(setOff.metres()).isEqualTo(99);
    }

    @Test
    void namesTheRoadATurnGoesOnto() {
        List<Turn> turns = turnsOf(
                instruction(Instruction.CONTINUE_ON_STREET, "", 99, Map.of("heading", 346.5)),
                instruction(Instruction.TURN_LEFT, "Te Putu Avenue", 68, Map.of()));

        assertThat(turns.getLast().way()).isEqualTo(Way.LEFT);
        assertThat(turns.getLast().street()).contains("Te Putu Avenue");
        // Only setting off has a bearing.
        assertThat(turns.getLast().heading()).isEmpty();
    }

    @Test
    void namesARoadByItsNumberWhenTheMapGivesItNoName() {
        Turn keepLeft = turnsOf(
                setOff(),
                instruction(Instruction.KEEP_LEFT, "", 2482, Map.of("street_ref", "SH 1"))).getLast();

        assertThat(keepLeft.street()).contains("SH 1");
    }

    @Test
    void keepsWhereTheSignsPointAndTheMotorwayExit() {
        Turn offTheMotorway = turnsOf(
                setOff(),
                instruction(Instruction.KEEP_LEFT, "", 293, Map.of(
                        "street_destination", "Airport",
                        "street_destination_ref", "12",
                        "motorway_junction", "Gillies Avenue"))).getLast();

        assertThat(offTheMotorway.way()).isEqualTo(Way.KEEP_LEFT);
        assertThat(offTheMotorway.towards()).contains("Airport");
        assertThat(offTheMotorway.motorwayExit()).contains("Gillies Avenue");
        // The number on the sign is the road's the signs point to, not the one turned onto.
        assertThat(offTheMotorway.street()).isEmpty();
    }

    @Test
    void countsTheExitsOfARoundabout() {
        RoundaboutInstruction roundabout = new RoundaboutInstruction(Instruction.USE_ROUNDABOUT, "Albert Road", at())
                .setExitNumber(2)
                .setExited();
        roundabout.setDistance(1004);

        Turn turn = turnsOf(setOff(), roundabout).getLast();

        assertThat(turn.way()).isEqualTo(Way.ROUNDABOUT);
        assertThat(turn.roundaboutExit()).hasValue(2);
        assertThat(turn.street()).contains("Albert Road");
    }

    @Test
    void arrivesWithNothingLeftToDrive() {
        Turn arrive = turnsOf(setOff(), instruction(Instruction.FINISH, "", 0, Map.of())).getLast();

        assertThat(arrive.way()).isEqualTo(Way.ARRIVE);
        assertThat(arrive.metres()).isZero();
    }

    @Test
    void readsEveryWayTheEngineTurns() {
        Map<Integer, Way> ways = Map.ofEntries(
                Map.entry(Instruction.CONTINUE_ON_STREET, Way.CONTINUE),
                Map.entry(Instruction.TURN_SLIGHT_LEFT, Way.SLIGHT_LEFT),
                Map.entry(Instruction.TURN_SHARP_LEFT, Way.SHARP_LEFT),
                Map.entry(Instruction.TURN_SLIGHT_RIGHT, Way.SLIGHT_RIGHT),
                Map.entry(Instruction.TURN_RIGHT, Way.RIGHT),
                Map.entry(Instruction.TURN_SHARP_RIGHT, Way.SHARP_RIGHT),
                Map.entry(Instruction.KEEP_RIGHT, Way.KEEP_RIGHT),
                Map.entry(Instruction.U_TURN_LEFT, Way.U_TURN),
                Map.entry(Instruction.U_TURN_RIGHT, Way.U_TURN),
                Map.entry(Instruction.U_TURN_UNKNOWN, Way.U_TURN),
                Map.entry(Instruction.LEAVE_ROUNDABOUT, Way.LEAVE_ROUNDABOUT),
                Map.entry(Instruction.FERRY, Way.FERRY),
                Map.entry(Instruction.REACHED_VIA, Way.CONTINUE));

        ways.forEach((sign, way) ->
                assertThat(turnsOf(setOff(), instruction(sign, "Some Road", 10, Map.of())).getLast().way())
                        .as("sign %d", sign)
                        .isEqualTo(way));
    }

    @Test
    void startsByLeavingTheRoundaboutWhenTheDriveStartsInsideOne() {
        Turn first = only(new RoundaboutInstruction(Instruction.LEAVE_ROUNDABOUT, "Queens Parade", at()));

        assertThat(first.way()).isEqualTo(Way.LEAVE_ROUNDABOUT);
        assertThat(first.heading()).isEmpty();
    }

    @Test
    void leavesOutTheExitOfARoundaboutTheDriveEndsInside() {
        Turn turn = only(new RoundaboutInstruction(Instruction.USE_ROUNDABOUT, "", at()));

        assertThat(turn.roundaboutExit()).isEmpty();
    }

    // The road a turn leads onto, when the turn goes onto one the map does not name.

    /**
     * Five points due south, a kilometre apart but the first two, which are 100 m apart: setting
     * off from point 0 to 1, then onto a ramp at 1, and on to point 4, where the drive arrives.
     */
    private static final PointList DUE_SOUTH = pointsAt(-36.9000, -36.9009, -36.9099, -36.9189, -36.9279);

    @Test
    void namesTheRoadAnUnnamedTurnLeadsOnto() {
        Map<String, List<PathDetail>> along = Map.of(
                Parameters.Details.STREET_NAME, List.of(detail(null, 0, 2), detail("Auckland Southern Motorway", 2, 4)));

        List<Turn> turns = alongTheRamp(along);

        assertThat(turns.get(1).street()).isEmpty();
        assertThat(turns.get(1).follows()).contains("Auckland Southern Motorway");
        // Setting off covers none of it, and arriving drives no further.
        assertThat(turns.getFirst().follows()).isEmpty();
        assertThat(turns.getLast().follows()).isEmpty();
    }

    @Test
    void takesTheRoadThatCoversTheMostOfTheStretchAndNotTheFirst() {
        Map<String, List<PathDetail>> along = Map.of(
                Parameters.Details.STREET_NAME, List.of(detail("Mount Wellington Highway", 1, 2), detail("Auckland Southern Motorway", 2, 4)));

        assertThat(alongTheRamp(along).get(1).follows()).contains("Auckland Southern Motorway");
    }

    @Test
    void takesTheRoadsNumberWhenTheStretchHasNoNames() {
        Map<String, List<PathDetail>> along = Map.of(
                Parameters.Details.STREET_NAME, List.of(detail(null, 0, 4)),
                Parameters.Details.STREET_REF, List.of(detail(null, 0, 2), detail("SH 1", 2, 4)));

        assertThat(alongTheRamp(along).get(1).follows()).contains("SH 1");
    }

    @Test
    void namesNoOtherRoadForATurnOntoANamedOne() {
        InstructionList instructions = new InstructionList(null);
        instructions.add(instructionOver(Instruction.CONTINUE_ON_STREET, "", 1));
        instructions.add(instructionOver(Instruction.TURN_LEFT, "Te Putu Avenue", 3));
        instructions.add(instructionOver(Instruction.FINISH, "", 0));
        Map<String, List<PathDetail>> along = Map.of(
                Parameters.Details.STREET_NAME, List.of(detail("Te Putu Avenue", 1, 2), detail("Auckland Southern Motorway", 2, 4)));

        Turn left = TurnsFromTheEngine.from(instructions, DUE_SOUTH, along).get(1);

        assertThat(left.street()).contains("Te Putu Avenue");
        assertThat(left.follows()).isEmpty();
    }

    @Test
    void saysOnlyWhichWayWhenNothingAlongTheStretchIsNamed() {
        Map<String, List<PathDetail>> along = Map.of(Parameters.Details.STREET_NAME, List.of(detail(null, 0, 4)));

        assertThat(alongTheRamp(along).get(1).follows()).isEmpty();
    }

    /** Setting off, a sharp right onto a ramp the map does not name, and arriving. */
    private static List<Turn> alongTheRamp(Map<String, List<PathDetail>> along) {
        InstructionList instructions = new InstructionList(null);
        instructions.add(instructionOver(Instruction.CONTINUE_ON_STREET, "", 1));
        instructions.add(instructionOver(Instruction.TURN_SHARP_RIGHT, "", 3));
        instructions.add(instructionOver(Instruction.FINISH, "", 0));
        return TurnsFromTheEngine.from(instructions, DUE_SOUTH, along);
    }

    /** An instruction whose stretch takes as many points of the drive as given. */
    private static Instruction instructionOver(int sign, String name, int points) {
        PointList its = new PointList(points, false);
        for (int point = 0; point < points; point++) {
            its.add(-36.9, 174.8);
        }
        return new Instruction(sign, name, its);
    }

    private static PathDetail detail(String value, int first, int last) {
        PathDetail detail = new PathDetail(value);
        detail.setFirst(first);
        detail.setLast(last);
        return detail;
    }

    private static PointList pointsAt(double... latitudes) {
        PointList points = new PointList(latitudes.length, false);
        for (double latitude : latitudes) {
            points.add(latitude, 174.8);
        }
        return points;
    }

    private static Instruction setOff() {
        return instruction(Instruction.CONTINUE_ON_STREET, "", 99, Map.of("heading", 346.5));
    }

    private static Instruction instruction(int sign, String name, double metres, Map<String, Object> extra) {
        Instruction instruction = new Instruction(sign, name, at());
        instruction.setDistance(metres);
        extra.forEach(instruction::setExtraInfo);
        return instruction;
    }

    private static PointList at() {
        PointList point = new PointList(1, false);
        point.add(-36.9170, 174.8414);
        return point;
    }

    private static Turn only(Instruction instruction) {
        return turnsOf(instruction).getFirst();
    }

    private static List<Turn> turnsOf(Instruction... instructions) {
        InstructionList list = new InstructionList(null);
        for (Instruction instruction : instructions) {
            list.add(instruction);
        }
        return TurnsFromTheEngine.from(list);
    }
}
