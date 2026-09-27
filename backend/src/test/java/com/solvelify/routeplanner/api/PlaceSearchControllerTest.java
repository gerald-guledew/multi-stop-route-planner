package com.solvelify.routeplanner.api;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solvelify.routeplanner.address.AddressSearch;
import com.solvelify.routeplanner.planning.Location;
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
    private AddressSearch addressSearch;

    @Test
    void returnsMatchesAsJson() throws Exception {
        given(addressSearch.search(anyString(), anyInt()))
                .willReturn(List.of(new Location("20A Bassett Road, Remuera", -36.8712, 174.7866)));

        mockMvc.perform(get("/api/v1/places/search").param("q", "bassett road"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("20A Bassett Road, Remuera"))
                .andExpect(jsonPath("$[0].latitude").value(-36.8712))
                .andExpect(jsonPath("$[0].longitude").value(174.7866));
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
