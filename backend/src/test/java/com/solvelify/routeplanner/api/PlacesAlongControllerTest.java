package com.solvelify.routeplanner.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solvelify.routeplanner.along.PlaceAlong;
import com.solvelify.routeplanner.along.PlacesAlongTheRoute;
import com.solvelify.routeplanner.planning.Location;
import com.solvelify.routeplanner.search.FoundPlace;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(PlacesAlongController.class)
class PlacesAlongControllerTest {

    private static final String SYLVIA_PARK = """
            {"name": "Sylvia Park", "latitude": -36.9170, "longitude": 174.8414}""";
    private static final String SKY_TOWER = """
            {"name": "Sky Tower", "latitude": -36.8485, "longitude": 174.7621}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlacesAlongTheRoute placesAlongTheRoute;

    @Test
    void givesEachPlaceWithTheLegItIsOnAndWhatItAdds() throws Exception {
        given(placesAlongTheRoute.find(anyString(), any(), any(), anyInt())).willReturn(List.of(new PlaceAlong(
                FoundPlace.poi("Z Ellerslie", "1 Main Hwy", "Ellerslie", "Auckland", -36.8990, 174.8130),
                0, 1.23456, OptionalDouble.of(184.6))));

        ask("""
                {"q": "z ellerslie", "route": [%s, %s]}""".formatted(SYLVIA_PARK, SKY_TOWER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].kind").value("poi"))
                .andExpect(jsonPath("$[0].name").value("Z Ellerslie"))
                .andExpect(jsonPath("$[0].detail").value("1 Main Hwy, Ellerslie, Auckland"))
                .andExpect(jsonPath("$[0].latitude").value(-36.8990))
                .andExpect(jsonPath("$[0].longitude").value(174.8130))
                .andExpect(jsonPath("$[0].leg").value(0))
                .andExpect(jsonPath("$[0].kmAdded").value(1.23))
                .andExpect(jsonPath("$[0].secondsAdded").value(185));
    }

    @Test
    void saysNothingOfTheTimeOnStraightLines() throws Exception {
        given(placesAlongTheRoute.find(anyString(), any(), any(), anyInt())).willReturn(List.of(new PlaceAlong(
                FoundPlace.address("1 Main Highway, Ellerslie", -36.8990, 174.8130), 0, 0.4, OptionalDouble.empty())));

        ask("""
                {"q": "1 main highway", "route": [%s, %s]}""".formatted(SYLVIA_PARK, SKY_TOWER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("address"))
                .andExpect(jsonPath("$[0].secondsAdded").doesNotExist());
    }

    @Test
    void looksAlongTheWholeRouteForEightPlacesUnlessToldOtherwise() throws Exception {
        ask("""
                {"q": "fuel", "route": [%s, %s, %s]}""".formatted(SYLVIA_PARK, SKY_TOWER, SYLVIA_PARK))
                .andExpect(status().isOk());

        verify(placesAlongTheRoute).find(eq("fuel"), eq(List.of(
                new Location("Sylvia Park", -36.9170, 174.8414),
                new Location("Sky Tower", -36.8485, 174.7621),
                new Location("Sylvia Park", -36.9170, 174.8414))), eq(OptionalInt.empty()), eq(8));
    }

    @Test
    void looksAlongTheLegAskedForAsManyAsAsked() throws Exception {
        ask("""
                {"q": "fuel", "route": [%s, %s, %s], "leg": 1, "limit": 3}""".formatted(SYLVIA_PARK, SKY_TOWER, SYLVIA_PARK))
                .andExpect(status().isOk());

        verify(placesAlongTheRoute).find(eq("fuel"), any(), eq(OptionalInt.of(1)), eq(3));
    }

    @Test
    void refusesALegThatIsNotOnTheRoute() throws Exception {
        ask("""
                {"q": "fuel", "route": [%s, %s], "leg": 1}""".formatted(SYLVIA_PARK, SKY_TOWER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("legOnTheRoute"));
    }

    @Test
    void refusesARouteOfOnePlace() throws Exception {
        ask("""
                {"q": "fuel", "route": [%s]}""".formatted(SYLVIA_PARK))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("route"));
    }

    @Test
    void refusesNothingTyped() throws Exception {
        ask("""
                {"q": " ", "route": [%s, %s]}""".formatted(SYLVIA_PARK, SKY_TOWER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("q"));
    }

    @Test
    void refusesAPlaceOfTheRouteThatIsNotOnEarth() throws Exception {
        ask("""
                {"q": "fuel", "route": [%s, {"name": "Nowhere", "latitude": 500, "longitude": 174.8}]}"""
                .formatted(SYLVIA_PARK))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("route[1].latitude"));
    }

    private ResultActions ask(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/places/along").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
