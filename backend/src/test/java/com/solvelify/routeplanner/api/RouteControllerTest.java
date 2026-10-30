package com.solvelify.routeplanner.api;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solvelify.routeplanner.planning.ChosenFor;
import com.solvelify.routeplanner.planning.ClimbAndDescent;
import com.solvelify.routeplanner.planning.FuelUse;
import com.solvelify.routeplanner.planning.Location;
import com.solvelify.routeplanner.planning.RoutePlan;
import com.solvelify.routeplanner.planning.RoutePlanningService;
import com.solvelify.routeplanner.planning.Turn;
import com.solvelify.routeplanner.planning.UnroutablePlaceException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The web layer on its own. The planning service is a stand-in, because what matters here is
 * the HTTP contract: status codes, JSON shape and validation messages.
 */
@WebMvcTest(RouteController.class)
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoutePlanningService routePlanningService;

    private static final String VALID_REQUEST = """
            {
              "start": { "name": "Sky Tower", "latitude": -36.8485, "longitude": 174.7621 },
              "stops": [
                { "name": "Takapuna", "latitude": -36.7870, "longitude": 174.7740 }
              ],
              "returnToStart": true
            }
            """;

    @Test
    void returnsThePlanAsJson() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.length()").value(3))
                .andExpect(jsonPath("$.route[0].name").value("Sky Tower"))
                .andExpect(jsonPath("$.legs.length()").value(2))
                .andExpect(jsonPath("$.totalDistanceKm").value(13.84))
                .andExpect(jsonPath("$.enteredOrderDistanceKm").value(13.84))
                .andExpect(jsonPath("$.ordersChecked").value(1));
    }

    @Test
    void roundsDistancesToTwoDecimals() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(jsonPath("$.legs[0].distanceKm").value(6.92));
    }

    @Test
    void givesTheFuelEachLegTakesForTheVehicleSent() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"returnToStart\": true",
                                "\"returnToStart\": true, \"vehicle\": { \"litresPer100Km\": 7.5 }")))
                .andExpect(status().isOk())
                // 6.918 km at 7.5 L/100 km is 0.519 L, rounded to a hundredth as distances are.
                .andExpect(jsonPath("$.legs[0].fuelLitres").value(0.52))
                .andExpect(jsonPath("$.legs[1].fuelLitres").value(0.52))
                .andExpect(jsonPath("$.totalFuelLitres").value(1.04));
    }

    // Town and the open road.

    @Test
    void passesOnTheTownAndOpenRoadFiguresSent() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"returnToStart\": true", "\"returnToStart\": true, \"vehicle\": "
                                + "{ \"litresPer100Km\": 7.5, \"townLitresPer100Km\": 9.0, \"openRoadLitresPer100Km\": 6.0, \"kilograms\": 1225 }")))
                .andExpect(status().isOk());

        verify(routePlanningService).plan(any(), any(), any(), any(), eq(true), eq(ChosenFor.DISTANCE),
                eq(Optional.of(new FuelUse(7.5, 9.0, 6.0, OptionalDouble.of(1225)))));
    }

    @Test
    void refusesATownFigureWithoutTheOpenRoads() throws Exception {
        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"returnToStart\": true", "\"returnToStart\": true, \"vehicle\": "
                                + "{ \"litresPer100Km\": 7.5, \"townLitresPer100Km\": 9.0 }")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("vehicle.townAndOpenRoad"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("must send townLitresPer100Km and openRoadLitresPer100Km together, or neither"));
    }

    @Test
    void takesTheTownFigureForTheKilometresInTown() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any()))
                .willReturn(aPlanWhoseRoadsSpeedsAreKnown());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"returnToStart\": true", "\"returnToStart\": true, \"vehicle\": "
                                + "{ \"litresPer100Km\": 7.5, \"townLitresPer100Km\": 9.0, \"openRoadLitresPer100Km\": 6.0 }")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuelCountsSpeed").value(true))
                // 4 km in town at 9 L/100 km and 5.412 km on the open road at 6: 0.685 L.
                .andExpect(jsonPath("$.legs[0].fuelLitres").value(0.68))
                // 5 km in town and 4.873 km on the open road: 0.742 L.
                .andExpect(jsonPath("$.legs[1].fuelLitres").value(0.74))
                .andExpect(jsonPath("$.totalFuelLitres").value(1.43));
    }

    @Test
    void saysTheFuelDoesNotCountTheSpeedOnStraightLines() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"returnToStart\": true",
                                "\"returnToStart\": true, \"vehicle\": { \"litresPer100Km\": 7.5 }")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuelCountsSpeed").value(false));
    }

    @Test
    void saysNothingAboutFuelWithoutAVehicle() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("totalFuelLitres")))
                .andExpect(jsonPath("$.totalFuelLitres").value(nullValue()))
                .andExpect(jsonPath("$.fuelCountsHills").value(nullValue()))
                .andExpect(jsonPath("$.fuelCountsSpeed").value(nullValue()))
                .andExpect(jsonPath("$.legs[0].fuelLitres").value(nullValue()));
    }

    @Test
    void countsTheHillsInTheFuelWhenTheWeightIsSent() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlanWithClimbs());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"returnToStart\": true",
                                "\"returnToStart\": true, \"vehicle\": { \"litresPer100Km\": 7.5, \"kilograms\": 1225 }")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuelCountsHills").value(true))
                // Up 86 m and down 41 m to Takapuna, the other way back. 0.71 L and 0.74 L on
                // the level, and the way there is the one that climbs.
                .andExpect(jsonPath("$.legs[0].fuelLitres").value(0.76))
                .andExpect(jsonPath("$.legs[1].fuelLitres").value(0.68))
                // The descents are gentle enough to give back what the climbs cost.
                .andExpect(jsonPath("$.totalFuelLitres").value(1.45));
    }

    @Test
    void doesNotCountTheHillsWithoutTheWeight() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlanWithClimbs());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"returnToStart\": true",
                                "\"returnToStart\": true, \"vehicle\": { \"litresPer100Km\": 7.5 }")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuelCountsHills").value(false))
                .andExpect(jsonPath("$.legs[0].fuelLitres").value(0.71));
    }

    @Test
    void doesNotCountTheHillsOfRoadsReadWithoutHeights() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"returnToStart\": true",
                                "\"returnToStart\": true, \"vehicle\": { \"litresPer100Km\": 7.5, \"kilograms\": 1225 }")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuelCountsHills").value(false))
                .andExpect(jsonPath("$.totalFuelLitres").value(1.04));
    }

    @Test
    void refusesAWeightNoCarHas() throws Exception {
        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"returnToStart\": true",
                                "\"returnToStart\": true, \"vehicle\": { \"litresPer100Km\": 7.5, \"kilograms\": 120 }")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("vehicle.kilograms"))
                .andExpect(jsonPath("$.errors[0].message").value("must be between 400 and 4000"));
    }

    @Test
    void refusesAVehicleWithAFigureNoPetrolCarShows() throws Exception {
        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"returnToStart\": true",
                                "\"returnToStart\": true, \"vehicle\": { \"litresPer100Km\": 75 }")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("vehicle.litresPer100Km"))
                .andExpect(jsonPath("$.errors[0].message").value("must be between 2 and 30"));
    }

    @Test
    void rejectsACoordinateThatCannotExist() throws Exception {
        String impossibleLatitude = """
                {
                  "start": { "name": "Sky Tower", "latitude": -36.8485, "longitude": 174.7621 },
                  "stops": [
                    { "name": "Nowhere", "latitude": 95.0, "longitude": 174.7740 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(impossibleLatitude))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("stops[0].latitude"))
                .andExpect(jsonPath("$.errors[0].message").value("must be between -90 and 90"));
    }

    @Test
    void rejectsARequestWithNoStops() throws Exception {
        String noStops = """
                {
                  "start": { "name": "Sky Tower", "latitude": -36.8485, "longitude": 174.7621 },
                  "stops": []
                }
                """;

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noStops))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("stops"))
                .andExpect(jsonPath("$.errors[0].message").value("at least one stop is required"));
    }

    @Test
    void rejectsAMissingName() throws Exception {
        String namelessStop = """
                {
                  "start": { "name": "Sky Tower", "latitude": -36.8485, "longitude": 174.7621 },
                  "stops": [
                    { "latitude": -36.7870, "longitude": 174.7740 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(namelessStop))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("stops[0].name"));
    }

    @Test
    void reportsAPlaceWithNoRoadNearItAsUnprocessable() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any()))
                .willThrow(new UnroutablePlaceException("Takapuna", "No road was found near those coordinates."));

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unroutable place"))
                .andExpect(jsonPath("$.place").value("Takapuna"))
                .andExpect(jsonPath("$.detail").value(
                        "Cannot route to Takapuna. No road was found near those coordinates."));
    }

    // Stops kept in their place.

    @Test
    void passesOnWhichStopsHaveToKeepTheirPlace() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());
        String officeFirst = """
                {
                  "start": { "name": "Home", "latitude": -36.9146, "longitude": 174.8392 },
                  "stops": [
                    { "name": "Office", "latitude": -36.8485, "longitude": 174.7621, "keepInPlace": true },
                    { "name": "Sylvia Park", "latitude": -36.9170, "longitude": 174.8414 },
                    { "name": "Supermarket", "latitude": -36.8982, "longitude": 174.8470, "keepInPlace": false },
                    { "name": "Airport", "latitude": -37.0070, "longitude": 174.7830, "keepInPlace": true }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officeFirst))
                .andExpect(status().isOk());

        // Positions in the list of stops, counting from 0: the first and the fourth.
        verify(routePlanningService).plan(any(), any(), eq(Set.of(0, 3)), any(), eq(true), any(), any());
    }

    @Test
    void passesOnWhichStopsAreStopOvers() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());
        String stopOverSecond = """
                {
                  "start": { "name": "Home", "latitude": -36.9146, "longitude": 174.8392 },
                  "stops": [
                    { "name": "Office", "latitude": -36.8485, "longitude": 174.7621 },
                    { "name": "Supermarket", "latitude": -36.8982, "longitude": 174.8470, "stopOver": true },
                    { "name": "Airport", "latitude": -37.0070, "longitude": 174.7830, "stopOver": false }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stopOverSecond))
                .andExpect(status().isOk());

        verify(routePlanningService).plan(any(), any(), eq(Set.of()), eq(Set.of(1)), eq(true), any(), any());
    }

    @Test
    void leavesEveryStopFreeToMoveUnlessToldOtherwise() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk());

        verify(routePlanningService).plan(any(), any(), eq(Set.of()), any(), eq(true), eq(ChosenFor.DISTANCE), any());
    }

    @Test
    void ignoresTheMarkOnTheStartWhichIsAlwaysFirstAnyway() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());
        String startMarked = """
                {
                  "start": { "name": "Home", "latitude": -36.9146, "longitude": 174.8392, "keepInPlace": true },
                  "stops": [
                    { "name": "Office", "latitude": -36.8485, "longitude": 174.7621 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startMarked))
                .andExpect(status().isOk());

        verify(routePlanningService).plan(any(), any(), eq(Set.of()), any(), eq(true), eq(ChosenFor.DISTANCE), any());
    }

    // Driving times.

    @Test
    void givesDrivingTimesInWholeSeconds() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlanWithTimes());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legs[0].durationSeconds").value(754))
                .andExpect(jsonPath("$.legs[1].durationSeconds").value(802))
                .andExpect(jsonPath("$.totalDurationSeconds").value(1556));
    }

    @Test
    void saysNullAndNotZeroWhenThereAreNoTimesToGive() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("totalDurationSeconds")))
                .andExpect(jsonPath("$.totalDurationSeconds").value(nullValue()))
                .andExpect(jsonPath("$.legs[0]", hasKey("durationSeconds")))
                .andExpect(jsonPath("$.legs[0].durationSeconds").value(nullValue()));
    }

    // Climb and descent.

    @Test
    void givesClimbAndDescentInWholeMetres() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlanWithClimbs());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legs[0].climbMetres").value(86))
                .andExpect(jsonPath("$.legs[0].descentMetres").value(41))
                .andExpect(jsonPath("$.legs[1].climbMetres").value(41))
                .andExpect(jsonPath("$.legs[1].descentMetres").value(87))
                // Totalled before rounding, so 127.0 and 127.1 say the same thing here. The
                // one-way trip below ends higher than it began and tells the two apart.
                .andExpect(jsonPath("$.totalClimbMetres").value(127))
                .andExpect(jsonPath("$.totalDescentMetres").value(127));
    }

    @Test
    void keepsTheClimbOfTheTripApartFromItsDescent() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aOneWayPlanUphill());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClimbMetres").value(86))
                .andExpect(jsonPath("$.totalDescentMetres").value(41));
    }

    @Test
    void saysNoughtAndNotNullForALegThatIsLevel() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aLevelPlan());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legs[0].climbMetres").value(0))
                .andExpect(jsonPath("$.legs[0].descentMetres").value(0))
                .andExpect(jsonPath("$.totalClimbMetres").value(0))
                .andExpect(jsonPath("$.totalDescentMetres").value(0));
    }

    @Test
    void saysNullAndNotNoughtWhenTheRoadsHaveNoHeights() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlanWithTimes());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("totalClimbMetres")))
                .andExpect(jsonPath("$.totalClimbMetres").value(nullValue()))
                .andExpect(jsonPath("$", hasKey("totalDescentMetres")))
                .andExpect(jsonPath("$.totalDescentMetres").value(nullValue()))
                .andExpect(jsonPath("$.legs[0]", hasKey("climbMetres")))
                .andExpect(jsonPath("$.legs[0].climbMetres").value(nullValue()))
                .andExpect(jsonPath("$.legs[0]", hasKey("descentMetres")))
                .andExpect(jsonPath("$.legs[0].descentMetres").value(nullValue()))
                // The times are still there. Only the heights are missing.
                .andExpect(jsonPath("$.totalDurationSeconds").value(1556));
    }

    // An order chosen for driving time.

    @Test
    void asksForTimeWhenToldToFindTheFastestOrder() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"returnToStart\": true", "\"fastest\": true")))
                .andExpect(status().isOk());

        verify(routePlanningService).plan(any(), any(), any(), any(), eq(true), eq(ChosenFor.TIME), any());
    }

    @Test
    void asksForDistanceWhenToldNotToFindTheFastestOrder() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"returnToStart\": true", "\"fastest\": false")))
                .andExpect(status().isOk());

        verify(routePlanningService).plan(any(), any(), any(), any(), eq(true), eq(ChosenFor.DISTANCE), any());
    }

    @Test
    void saysHowLongTheShortestOrderTakesWhenTheOrderWasChosenForTime() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlanChosenForTime());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"returnToStart\": true", "\"fastest\": true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDurationSeconds").value(1556))
                .andExpect(jsonPath("$.shortestOrder.distanceKm").value(18.94))
                .andExpect(jsonPath("$.shortestOrder.durationSeconds").value(1746))
                // Roads read without heights. Null, as for the plan, and not nought.
                .andExpect(jsonPath("$.shortestOrder", hasKey("climbMetres")))
                .andExpect(jsonPath("$.shortestOrder.climbMetres").value(nullValue()))
                .andExpect(jsonPath("$.shortestOrder.descentMetres").value(nullValue()));
    }

    @Test
    void saysWhatTheShortestOrderComesTo() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any()))
                .willReturn(aPlanChosenForFuel());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDistanceKm").value(19.29))
                .andExpect(jsonPath("$.shortestOrder.distanceKm").value(18.94))
                .andExpect(jsonPath("$.shortestOrder.durationSeconds").value(1490))
                .andExpect(jsonPath("$.shortestOrder.climbMetres").value(153))
                .andExpect(jsonPath("$.shortestOrder.descentMetres").value(152));
    }

    @Test
    void saysNullForTheShortestOrderWhenThePlanIsTheShortest() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlanWithClimbs());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("shortestOrder")))
                .andExpect(jsonPath("$.shortestOrder").value(nullValue()));
    }

    // An order chosen for the least fuel.

    private static final String FOR_THE_LEAST_FUEL = VALID_REQUEST.replace(
            "\"returnToStart\": true",
            "\"leastFuel\": true, \"vehicle\": { \"litresPer100Km\": 7.5, \"kilograms\": 1225 }");

    @Test
    void asksForTheLeastFuelOfTheVehicleSent() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FOR_THE_LEAST_FUEL))
                .andExpect(status().isOk());

        verify(routePlanningService).plan(any(), any(), any(), any(), eq(true), eq(ChosenFor.FUEL),
                eq(Optional.of(new FuelUse(7.5, OptionalDouble.of(1225)))));
    }

    @Test
    void passesTheVehicleOnWhateverTheOrderIsChosenFor() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FOR_THE_LEAST_FUEL.replace("\"leastFuel\": true", "\"leastFuel\": false")))
                .andExpect(status().isOk());

        verify(routePlanningService).plan(any(), any(), any(), any(), eq(true), eq(ChosenFor.DISTANCE),
                eq(Optional.of(new FuelUse(7.5, OptionalDouble.of(1225)))));
    }

    @Test
    void refusesTheLeastFuelWithoutAVehicle() throws Exception {
        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"returnToStart\": true", "\"leastFuel\": true")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("leastFuelForAVehicle"))
                .andExpect(jsonPath("$.errors[0].message").value("must send the vehicle whose fuel is to be the least"));
    }

    @Test
    void refusesTheLeastFuelWithTheFastestOrder() throws Exception {
        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FOR_THE_LEAST_FUEL.replace("\"leastFuel\": true", "\"leastFuel\": true, \"fastest\": true")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("leastFuelAlone"))
                .andExpect(jsonPath("$.errors[0].message").value("must ask for the least fuel or the fastest order, not both"));
    }

    @Test
    void saysWhatFuelTheShortestOrderTakes() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any()))
                .willReturn(aPlanChosenForFuel());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FOR_THE_LEAST_FUEL))
                .andExpect(status().isOk())
                // 18.94 km at 7.5 L/100 km, its 153 m up given back on the way down, to a hundredth.
                .andExpect(jsonPath("$.shortestOrder.fuelLitres").value(1.42));
    }

    @Test
    void saysNullForTheFuelOfTheShortestOrderWithoutAVehicle() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any()))
                .willReturn(aPlanChosenForFuel());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortestOrder", hasKey("fuelLitres")))
                .andExpect(jsonPath("$.shortestOrder.fuelLitres").value(nullValue()));
    }

    // The other of the two choices.

    private static final String TOWN_AND_OPEN_ROAD =
            "\"vehicle\": { \"litresPer100Km\": 7.5, \"townLitresPer100Km\": 9.0, \"openRoadLitresPer100Km\": 6.0 }";

    @Test
    void givesTheFastestOrderBesideTheLeastFuel() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any()))
                .willReturn(aPlanWithTheOtherChoiceBesideIt(ChosenFor.TIME));

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"returnToStart\": true", "\"leastFuel\": true, " + TOWN_AND_OPEN_ROAD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fastestOrder.distanceKm").value(20.31))
                .andExpect(jsonPath("$.fastestOrder.durationSeconds").value(1312))
                .andExpect(jsonPath("$.fastestOrder.climbMetres").value(nullValue()))
                // 3 km in town at 9 L/100 km and 17.312 km on the open road at 6: 1.309 L.
                .andExpect(jsonPath("$.fastestOrder.fuelLitres").value(1.31))
                .andExpect(jsonPath("$", hasKey("leastFuelOrder")))
                .andExpect(jsonPath("$.leastFuelOrder").value(nullValue()));
    }

    @Test
    void givesTheLeastFuelOrderBesideTheFastest() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any()))
                .willReturn(aPlanWithTheOtherChoiceBesideIt(ChosenFor.FUEL));

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"returnToStart\": true", "\"fastest\": true, " + TOWN_AND_OPEN_ROAD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leastFuelOrder.distanceKm").value(20.31))
                .andExpect(jsonPath("$.leastFuelOrder.durationSeconds").value(1312))
                .andExpect(jsonPath("$.leastFuelOrder.fuelLitres").value(1.31))
                .andExpect(jsonPath("$", hasKey("fastestOrder")))
                .andExpect(jsonPath("$.fastestOrder").value(nullValue()));
    }

    @Test
    void saysNullForBothChoicesWhenThePlanGivesNoOther() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("fastestOrder")))
                .andExpect(jsonPath("$.fastestOrder").value(nullValue()))
                .andExpect(jsonPath("$", hasKey("leastFuelOrder")))
                .andExpect(jsonPath("$.leastFuelOrder").value(nullValue()));
    }

    /**
     * The plan on roads whose speeds are known further down, with the other choice beside it:
     * 20.312 km in 1312.4 seconds, 3 km of it in town.
     */
    private static RoutePlan aPlanWithTheOtherChoiceBesideIt(ChosenFor other) {
        RoutePlan chosen = aPlanWhoseRoadsSpeedsAreKnown();

        return new RoutePlan(
                chosen.route(),
                chosen.legs(),
                chosen.totalDistanceKm(),
                chosen.totalDurationSeconds(),
                chosen.totalClimbAndDescent(),
                chosen.enteredOrderDistanceKm(),
                chosen.ordersChecked(),
                Optional.of(new RoutePlan.ShortestOrder(19.285, OptionalDouble.of(1556.0), Optional.empty(), OptionalDouble.of(9))),
                Optional.of(new RoutePlan.OtherChoice(
                        other, 20.312, OptionalDouble.of(1312.4), Optional.empty(), OptionalDouble.of(3))));
    }

    // The turns of each leg.

    @Test
    void givesTheTurnsOfEachLeg() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlanWithTurns());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legs[0].turns.length()").value(6))
                // Setting off, on a bearing to the whole degree, down a drive with no name.
                .andExpect(jsonPath("$.legs[0].turns[0].turn").value("setOff"))
                .andExpect(jsonPath("$.legs[0].turns[0].heading").value(347))
                .andExpect(jsonPath("$.legs[0].turns[0].street").value(nullValue()))
                .andExpect(jsonPath("$.legs[0].turns[0].distanceMetres").value(99))
                .andExpect(jsonPath("$.legs[0].turns[1].turn").value("left"))
                .andExpect(jsonPath("$.legs[0].turns[1].street").value("Te Putu Avenue"))
                .andExpect(jsonPath("$.legs[0].turns[1].heading").value(nullValue()))
                .andExpect(jsonPath("$.legs[0].turns[2].turn").value("roundabout"))
                .andExpect(jsonPath("$.legs[0].turns[2].roundaboutExit").value(2))
                // Onto a ramp the map does not name, and the motorway it leads to.
                .andExpect(jsonPath("$.legs[0].turns[3].turn").value("sharpRight"))
                .andExpect(jsonPath("$.legs[0].turns[3].street").value(nullValue()))
                .andExpect(jsonPath("$.legs[0].turns[3].follows").value("Auckland Southern Motorway"))
                .andExpect(jsonPath("$.legs[0].turns[4].turn").value("keepLeft"))
                .andExpect(jsonPath("$.legs[0].turns[4].motorwayExit").value("Market Road"))
                .andExpect(jsonPath("$.legs[0].turns[4].towards").value("Newmarket"))
                .andExpect(jsonPath("$.legs[0].turns[4].follows").value(nullValue()))
                .andExpect(jsonPath("$.legs[0].turns[4].roundaboutExit").value(nullValue()))
                .andExpect(jsonPath("$.legs[0].turns[5].turn").value("arrive"))
                .andExpect(jsonPath("$.legs[0].turns[5].distanceMetres").value(0));
    }

    @Test
    void givesNoTurnsOnStraightLines() throws Exception {
        given(routePlanningService.plan(any(), any(), any(), any(), anyBoolean(), any(), any())).willReturn(aPlan());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legs[0].turns").isArray())
                .andExpect(jsonPath("$.legs[0].turns.length()").value(0));
    }

    /** Sylvia Park to the museum, as far as the motorway exit, and one leg back on straight lines. */
    private static RoutePlan aPlanWithTurns() {
        Location sylviaPark = new Location("Sylvia Park", -36.9170, 174.8414);
        Location museum = new Location("Museum", -36.8604, 174.7777);
        Turn setOff = new Turn(Turn.Way.SET_OFF, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), OptionalInt.empty(), OptionalDouble.of(346.5), 99.4);
        Turn roundabout = new Turn(Turn.Way.ROUNDABOUT, Optional.of("Te Putu Avenue"), Optional.empty(),
                Optional.empty(), Optional.empty(), OptionalInt.of(2), OptionalDouble.empty(), 52);
        Turn ontoTheMotorway = new Turn(Turn.Way.SHARP_RIGHT, Optional.empty(), Optional.of("Auckland Southern Motorway"),
                Optional.empty(), Optional.empty(), OptionalInt.empty(), OptionalDouble.empty(), 6255);
        Turn offTheMotorway = new Turn(Turn.Way.KEEP_LEFT, Optional.empty(), Optional.empty(), Optional.of("Newmarket"),
                Optional.of("Market Road"), OptionalInt.empty(), OptionalDouble.empty(), 349);

        return new RoutePlan(
                List.of(sylviaPark, museum, sylviaPark),
                List.of(
                        new RoutePlan.Leg("Sylvia Park", "Museum", 10.744, OptionalDouble.of(642), Optional.empty(),
                                List.of(), List.of(setOff, Turn.onto(Turn.Way.LEFT, "Te Putu Avenue", 68), roundabout,
                                        ontoTheMotorway, offTheMotorway, Turn.of(Turn.Way.ARRIVE, 0))),
                        new RoutePlan.Leg("Museum", "Sylvia Park", 8.62, List.of())),
                19.364,
                19.364,
                1);
    }

    /** The plan with climbs further up, chosen for fuel, and longer than the shortest order. */
    private static RoutePlan aPlanChosenForFuel() {
        RoutePlan chosen = aPlanWithClimbs();

        return new RoutePlan(
                chosen.route(),
                chosen.legs(),
                chosen.totalDistanceKm(),
                chosen.totalDurationSeconds(),
                chosen.totalClimbAndDescent(),
                chosen.enteredOrderDistanceKm(),
                chosen.ordersChecked(),
                Optional.of(new RoutePlan.ShortestOrder(
                        18.936, OptionalDouble.of(1490.4), Optional.of(new ClimbAndDescent(152.6, 152.4)))));
    }

    /** The plan with times further down, chosen for time, on roads read without their heights. */
    private static RoutePlan aPlanChosenForTime() {
        RoutePlan chosen = aPlanWithTimes();

        return new RoutePlan(
                chosen.route(),
                chosen.legs(),
                chosen.totalDistanceKm(),
                chosen.totalDurationSeconds(),
                chosen.totalClimbAndDescent(),
                chosen.enteredOrderDistanceKm(),
                chosen.ordersChecked(),
                Optional.of(new RoutePlan.ShortestOrder(18.936, OptionalDouble.of(1745.6), Optional.empty())));
    }

    /** Sky Tower to Takapuna and back on roads whose speeds are known, 4 km and 5 km of it in town. */
    private static RoutePlan aPlanWhoseRoadsSpeedsAreKnown() {
        Location skyTower = new Location("Sky Tower", -36.8485, 174.7621);
        Location takapuna = new Location("Takapuna", -36.7870, 174.7740);

        return new RoutePlan(
                List.of(skyTower, takapuna, skyTower),
                List.of(
                        new RoutePlan.Leg("Sky Tower", "Takapuna", 9.412, OptionalDouble.of(754.4), Optional.empty(),
                                OptionalDouble.of(4), List.of(), List.of()),
                        new RoutePlan.Leg("Takapuna", "Sky Tower", 9.873, OptionalDouble.of(801.6), Optional.empty(),
                                OptionalDouble.of(5), List.of(), List.of())),
                19.285,
                OptionalDouble.of(1556.0),
                19.285,
                1);
    }

    private static RoutePlan aPlanWithClimbs() {
        Location skyTower = new Location("Sky Tower", -36.8485, 174.7621);
        Location takapuna = new Location("Takapuna", -36.7870, 174.7740);

        return new RoutePlan(
                List.of(skyTower, takapuna, skyTower),
                List.of(
                        new RoutePlan.Leg("Sky Tower", "Takapuna", 9.412, OptionalDouble.of(754.4),
                                Optional.of(new ClimbAndDescent(86.4, 40.6)), List.of()),
                        new RoutePlan.Leg("Takapuna", "Sky Tower", 9.873, OptionalDouble.of(801.6),
                                Optional.of(new ClimbAndDescent(40.6, 86.5)), List.of())),
                19.285,
                OptionalDouble.of(1556.0),
                Optional.of(new ClimbAndDescent(127.0, 127.1)),
                19.285,
                1);
    }

    private static RoutePlan aOneWayPlanUphill() {
        Location skyTower = new Location("Sky Tower", -36.8485, 174.7621);
        Location takapuna = new Location("Takapuna", -36.7870, 174.7740);

        return new RoutePlan(
                List.of(skyTower, takapuna),
                List.of(new RoutePlan.Leg("Sky Tower", "Takapuna", 9.412, OptionalDouble.of(754.4),
                        Optional.of(new ClimbAndDescent(86.4, 40.6)), List.of())),
                9.412,
                OptionalDouble.of(754.4),
                Optional.of(new ClimbAndDescent(86.4, 40.6)),
                9.412,
                1);
    }

    private static RoutePlan aLevelPlan() {
        Location skyTower = new Location("Sky Tower", -36.8485, 174.7621);
        Location takapuna = new Location("Takapuna", -36.7870, 174.7740);

        return new RoutePlan(
                List.of(skyTower, takapuna, skyTower),
                List.of(
                        new RoutePlan.Leg("Sky Tower", "Takapuna", 9.412, OptionalDouble.of(754.4),
                                Optional.of(ClimbAndDescent.LEVEL), List.of()),
                        new RoutePlan.Leg("Takapuna", "Sky Tower", 9.873, OptionalDouble.of(801.6),
                                Optional.of(ClimbAndDescent.LEVEL), List.of())),
                19.285,
                OptionalDouble.of(1556.0),
                Optional.of(ClimbAndDescent.LEVEL),
                19.285,
                1);
    }

    private static RoutePlan aPlanWithTimes() {
        Location skyTower = new Location("Sky Tower", -36.8485, 174.7621);
        Location takapuna = new Location("Takapuna", -36.7870, 174.7740);

        return new RoutePlan(
                List.of(skyTower, takapuna, skyTower),
                List.of(
                        new RoutePlan.Leg("Sky Tower", "Takapuna", 9.412, OptionalDouble.of(754.4), List.of()),
                        new RoutePlan.Leg("Takapuna", "Sky Tower", 9.873, OptionalDouble.of(801.6), List.of())),
                19.285,
                OptionalDouble.of(1556.0),
                19.285,
                1);
    }

    private static RoutePlan aPlan() {
        Location skyTower = new Location("Sky Tower", -36.8485, 174.7621);
        Location takapuna = new Location("Takapuna", -36.7870, 174.7740);

        return new RoutePlan(
                List.of(skyTower, takapuna, skyTower),
                List.of(
                        new RoutePlan.Leg("Sky Tower", "Takapuna", 6.918, List.of()),
                        new RoutePlan.Leg("Takapuna", "Sky Tower", 6.918, List.of())),
                13.836,
                13.836,
                1);
    }
}
