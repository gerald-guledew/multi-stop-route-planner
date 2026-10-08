package com.solvelify.routeplanner.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solvelify.routeplanner.search.FoundPlace;
import com.solvelify.routeplanner.search.PlaceSearch;
import com.solvelify.routeplanner.search.ReferencePoint;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlaceSearchController.class)
class PlaceSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceSearch placeSearch;

    @Test
    void returnsAnAddressAsJson() throws Exception {
        given(placeSearch.search(anyString(), any(), anyInt()))
                .willReturn(List.of(FoundPlace.address("20A Bassett Road, Remuera", -36.8712, 174.7866)));

        mockMvc.perform(get("/api/v1/places/search").param("q", "bassett road"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].kind").value("address"))
                .andExpect(jsonPath("$[0].name").value("20A Bassett Road, Remuera"))
                .andExpect(jsonPath("$[0].detail").doesNotExist())
                .andExpect(jsonPath("$[0].latitude").value(-36.8712))
                .andExpect(jsonPath("$[0].longitude").value(174.7866));
    }

    @Test
    void returnsANamedPlaceWithWhereItIs() throws Exception {
        given(placeSearch.search(anyString(), any(), anyInt()))
                .willReturn(List.of(FoundPlace.poi(
                        "New World Remuera", "10 Clonbern Rd", "Remuera", "Auckland", -36.8817, 174.7975)));

        mockMvc.perform(get("/api/v1/places/search").param("q", "new world remuera"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("poi"))
                .andExpect(jsonPath("$[0].name").value("New World Remuera"))
                .andExpect(jsonPath("$[0].detail").value("10 Clonbern Rd, Remuera, Auckland"));
    }

    @Test
    void rejectsAnEmptySearch() throws Exception {
        mockMvc.perform(get("/api/v1/places/search").param("q", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnAbsurdLimit() throws Exception {
        mockMvc.perform(get("/api/v1/places/search").param("q", "bassett").param("limit", "500"))
                .andExpect(status().isBadRequest());
    }

    // Where the search is looking from.

    @Test
    void searchesNearThePointItIsGiven() throws Exception {
        mockMvc.perform(get("/api/v1/places/search").param("q", "new world").param("near", "-36.871,174.787"))
                .andExpect(status().isOk());

        verify(placeSearch).search("new world", new ReferencePoint(-36.871, 174.787), 8);
    }

    @Test
    void allowsASpaceAfterTheComma() throws Exception {
        mockMvc.perform(get("/api/v1/places/search").param("q", "new world").param("near", "-36.871, 174.787"))
                .andExpect(status().isOk());

        verify(placeSearch).search("new world", new ReferencePoint(-36.871, 174.787), 8);
    }

    @Test
    void searchesOnTheWordsAloneWhenNoPointIsGiven() throws Exception {
        mockMvc.perform(get("/api/v1/places/search").param("q", "new world"))
                .andExpect(status().isOk());

        verify(placeSearch).search("new world", null, 8);
    }

    @Test
    void treatsAnEmptyPointAsNoPoint() throws Exception {
        mockMvc.perform(get("/api/v1/places/search").param("q", "new world").param("near", ""))
                .andExpect(status().isOk());

        verify(placeSearch).search("new world", null, 8);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "here", "-36.871", "-36.871,174.787,5", "-36.871,east", "91,174.787", "-36.871,181", "NaN,NaN"})
    void rejectsAPointThatIsNotALatitudeAndALongitude(String near) throws Exception {
        mockMvc.perform(get("/api/v1/places/search").param("q", "new world").param("near", near))
                .andExpect(status().isBadRequest())
                // Spring words the message. What matters is that it says which parameter was wrong.
                .andExpect(jsonPath("$.detail", containsString("'near'")));
    }
}
