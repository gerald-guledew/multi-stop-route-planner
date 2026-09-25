package com.solvelify.routeplanner.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The whole application, from JSON in to JSON out, with nothing mocked. The numbers match
 * docs/api.md, so this test fails if the documented contract and the code drift apart.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OptimizeRouteEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String AUCKLAND_ERRANDS = """
            {
              "start": { "name": "Sky Tower", "latitude": -36.8485, "longitude": 174.7621 },
              "stops": [
                { "name": "Takapuna",    "latitude": -36.7870, "longitude": 174.7740 },
                { "name": "Airport",     "latitude": -37.0082, "longitude": 174.7850 },
                { "name": "Devonport",   "latitude": -36.8330, "longitude": 174.7955 },
                { "name": "Sylvia Park", "latitude": -36.9170, "longitude": 174.8414 }
              ],
              "returnToStart": %s
            }
            """;

    @Test
    void plansTheRoundTripFromDocsApiMd() throws Exception {
        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AUCKLAND_ERRANDS.formatted("true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDistanceKm").value(51.76))
                .andExpect(jsonPath("$.enteredOrderDistanceKm").value(71.61))
                .andExpect(jsonPath("$.ordersChecked").value(24))
                .andExpect(jsonPath("$.route.length()").value(6))
                .andExpect(jsonPath("$.legs.length()").value(5));
    }

    @Test
    void plansTheOneWayTripFromDocsApiMd() throws Exception {
        mockMvc.perform(post("/api/v1/routes/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AUCKLAND_ERRANDS.formatted("false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDistanceKm").value(33.89))
                .andExpect(jsonPath("$.enteredOrderDistanceKm").value(61.23))
                .andExpect(jsonPath("$.route.length()").value(5))
                .andExpect(jsonPath("$.route[1].name").value("Takapuna"))
                .andExpect(jsonPath("$.route[4].name").value("Airport"))
                .andExpect(jsonPath("$.legs[0].distanceKm").value(6.92))
                .andExpect(jsonPath("$.legs[3].distanceKm").value(11.31));
    }
}
