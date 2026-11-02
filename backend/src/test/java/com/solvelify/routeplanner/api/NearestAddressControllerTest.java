package com.solvelify.routeplanner.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solvelify.routeplanner.search.NearbyAddress;
import com.solvelify.routeplanner.search.PlaceSearch;
import com.solvelify.routeplanner.search.ReferencePoint;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(NearestAddressController.class)
class NearestAddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceSearch placeSearch;

    @Test
    void givesTheAddressAndHowFarOffItIs() throws Exception {
        given(placeSearch.nearestAddress(any(), anyDouble()))
                .willReturn(Optional.of(new NearbyAddress("286 Mount Wellington Highway, Mount Wellington", 14.4)));

        ask("""
                {"latitude": -36.9170, "longitude": 174.8414}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("286 Mount Wellington Highway, Mount Wellington"))
                .andExpect(jsonPath("$.metres").value(14));

        verify(placeSearch).nearestAddress(eq(new ReferencePoint(-36.9170, 174.8414)), eq(1000.0));
    }

    @Test
    void answersNullWithNoAddressNearEnough() throws Exception {
        given(placeSearch.nearestAddress(any(), anyDouble())).willReturn(Optional.empty());

        ask("""
                {"latitude": -45.5, "longitude": 167.5}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.metres").doesNotExist());
    }

    @Test
    void refusesASpotThatIsNotOnEarth() throws Exception {
        ask("""
                {"latitude": -91, "longitude": 174.8414}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("latitude"));
    }

    @Test
    void refusesASpotWithAPartMissing() throws Exception {
        ask("""
                {"latitude": -36.9170}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("longitude"));
    }

    private ResultActions ask(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/places/nearest-address")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
