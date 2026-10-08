package com.solvelify.routeplanner.api;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solvelify.routeplanner.search.FoundPlace;
import com.solvelify.routeplanner.search.PlaceSearch;
import java.util.List;
import org.junit.jupiter.api.Test;
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
        given(placeSearch.search(anyString(), anyInt()))
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
        given(placeSearch.search(anyString(), anyInt()))
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
}
