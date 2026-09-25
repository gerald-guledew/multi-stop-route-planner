package com.solvelify.routeplanner.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solvelify.routeplanner.planning.Location;
import com.solvelify.routeplanner.planning.RoutePlan;
import com.solvelify.routeplanner.planning.RoutePlanningService;
import com.solvelify.routeplanner.planning.UnroutablePlaceException;
import java.util.List;
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
        given(routePlanningService.plan(any(), any(), anyBoolean())).willReturn(aPlan());

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
        given(routePlanningService.plan(any(), any(), anyBoolean())).willReturn(aPlan());

        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(jsonPath("$.legs[0].distanceKm").value(6.92));
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
        given(routePlanningService.plan(any(), any(), anyBoolean()))
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

    private static RoutePlan aPlan() {
        Location skyTower = new Location("Sky Tower", -36.8485, 174.7621);
        Location takapuna = new Location("Takapuna", -36.7870, 174.7740);

        return new RoutePlan(
                List.of(skyTower, takapuna, skyTower),
                List.of(
                        new RoutePlan.Leg("Sky Tower", "Takapuna", 6.918),
                        new RoutePlan.Leg("Takapuna", "Sky Tower", 6.918)),
                13.836,
                13.836,
                1);
    }
}
