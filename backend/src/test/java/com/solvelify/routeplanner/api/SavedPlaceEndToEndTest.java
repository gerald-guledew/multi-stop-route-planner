package com.solvelify.routeplanner.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.solvelify.routeplanner.saved.SavedPlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Saved places through the whole application, from JSON in to the table and back, with
 * nothing mocked. The requests are the ones in docs/api.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SavedPlaceEndToEndTest {

    private static final String HOME = """
            { "name": "Home", "latitude": -36.8689416667, "longitude": 174.7832303333 }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SavedPlaceRepository repository;

    @BeforeEach
    void emptyTheTable() {
        repository.deleteAllInBatch();
    }

    @Test
    void aPlaceIsSavedListedAndRemoved() throws Exception {
        mockMvc.perform(get("/api/v1/saved-places"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        String saved = mockMvc.perform(post("/api/v1/saved-places").contentType(MediaType.APPLICATION_JSON).content(HOME))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Home"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(saved, "$.id");

        // The position comes back to the last digit it was sent with.
        mockMvc.perform(get("/api/v1/saved-places"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].name").value("Home"))
                .andExpect(jsonPath("$[0].latitude").value(-36.8689416667))
                .andExpect(jsonPath("$[0].longitude").value(174.7832303333));

        mockMvc.perform(delete("/api/v1/saved-places/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/saved-places"))
                .andExpect(content().json("[]"));

        mockMvc.perform(delete("/api/v1/saved-places/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void theSameNameInOtherCapitalsIsAConflict() throws Exception {
        mockMvc.perform(post("/api/v1/saved-places").contentType(MediaType.APPLICATION_JSON).content(HOME))
                .andExpect(status().isCreated());

        String shouting = """
                { "name": "HOME", "latitude": -36.9000, "longitude": 174.8000 }
                """;
        mockMvc.perform(post("/api/v1/saved-places").contentType(MediaType.APPLICATION_JSON).content(shouting))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.name").value("HOME"));

        mockMvc.perform(get("/api/v1/saved-places"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Home"));
    }
}
