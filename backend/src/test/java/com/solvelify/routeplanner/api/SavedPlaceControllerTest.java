package com.solvelify.routeplanner.api;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solvelify.routeplanner.saved.PlaceNameTakenException;
import com.solvelify.routeplanner.saved.SavedPlace;
import com.solvelify.routeplanner.saved.SavedPlaceNotFoundException;
import com.solvelify.routeplanner.saved.SavedPlaces;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The web layer on its own, with a stand-in for the saved places themselves. What matters
 * here is the HTTP contract: status codes, JSON shape and what a refusal says.
 */
@WebMvcTest(SavedPlaceController.class)
class SavedPlaceControllerTest {

    private static final String HOME = """
            { "name": "Home", "latitude": -36.8689, "longitude": 174.7832 }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SavedPlaces savedPlaces;

    // Listing.

    @Test
    void listsSavedPlacesAsJson() throws Exception {
        given(savedPlaces.all()).willReturn(List.of(
                saved(7, "Home", -36.8689, 174.7832),
                saved(9, "The office", -36.8485, 174.7621)));

        mockMvc.perform(get("/api/v1/saved-places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].name").value("Home"))
                .andExpect(jsonPath("$[0].latitude").value(-36.8689))
                .andExpect(jsonPath("$[0].longitude").value(174.7832))
                .andExpect(jsonPath("$[1].name").value("The office"));
    }

    @Test
    void listsNothingAsAnEmptyList() throws Exception {
        given(savedPlaces.all()).willReturn(List.of());

        mockMvc.perform(get("/api/v1/saved-places"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void tellsTheBrowserToKeepNoCopyOfTheList() throws Exception {
        given(savedPlaces.all()).willReturn(List.of());

        mockMvc.perform(get("/api/v1/saved-places"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    // Saving.

    @Test
    void savesAPlaceAndAnswersWithItsId() throws Exception {
        given(savedPlaces.save("Home", -36.8689, 174.7832)).willReturn(saved(7, "Home", -36.8689, 174.7832));

        mockMvc.perform(post("/api/v1/saved-places").contentType(MediaType.APPLICATION_JSON).content(HOME))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Home"))
                .andExpect(jsonPath("$.latitude").value(-36.8689))
                .andExpect(jsonPath("$.longitude").value(174.7832));
    }

    @Test
    void refusesANameThatIsAlreadySaved() throws Exception {
        given(savedPlaces.save(anyString(), anyDouble(), anyDouble())).willThrow(new PlaceNameTakenException("Home"));

        mockMvc.perform(post("/api/v1/saved-places").contentType(MediaType.APPLICATION_JSON).content(HOME))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Name already saved"))
                .andExpect(jsonPath("$.detail").value(
                        "A place called \"Home\" is already saved. Remove it first, or save this one under another name."))
                .andExpect(jsonPath("$.instance").value("/api/v1/saved-places"))
                .andExpect(jsonPath("$.name").value("Home"));
    }

    @Test
    void rejectsAPlaceWithNoName() throws Exception {
        String nameless = """
                { "name": "  ", "latitude": -36.8689, "longitude": 174.7832 }
                """;

        mockMvc.perform(post("/api/v1/saved-places").contentType(MediaType.APPLICATION_JSON).content(nameless))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
        verifyNoInteractions(savedPlaces);
    }

    @Test
    void rejectsANameTooLongToBeALabel() throws Exception {
        String longName = """
                { "name": "%s", "latitude": -36.8689, "longitude": 174.7832 }
                """.formatted("x".repeat(101));

        mockMvc.perform(post("/api/v1/saved-places").contentType(MediaType.APPLICATION_JSON).content(longName))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("must be at most 100 characters"));
    }

    @Test
    void rejectsACoordinateThatCannotExist() throws Exception {
        String impossibleLatitude = """
                { "name": "Nowhere", "latitude": 95.0, "longitude": 174.7832 }
                """;

        mockMvc.perform(post("/api/v1/saved-places").contentType(MediaType.APPLICATION_JSON).content(impossibleLatitude))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("latitude"))
                .andExpect(jsonPath("$.errors[0].message").value("must be between -90 and 90"));
        verifyNoInteractions(savedPlaces);
    }

    @Test
    void rejectsAPlaceWithNoPosition() throws Exception {
        String nowhere = """
                { "name": "Home" }
                """;

        mockMvc.perform(post("/api/v1/saved-places").contentType(MediaType.APPLICATION_JSON).content(nowhere))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("latitude"))
                .andExpect(jsonPath("$.errors[1].field").value("longitude"));
    }

    // Removing.

    @Test
    void removesAPlaceByItsId() throws Exception {
        mockMvc.perform(delete("/api/v1/saved-places/7"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(savedPlaces).remove(7);
    }

    @Test
    void saysSoWhenThereIsNoSuchPlaceToRemove() throws Exception {
        willThrow(new SavedPlaceNotFoundException(7)).given(savedPlaces).remove(7);

        mockMvc.perform(delete("/api/v1/saved-places/7"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No such saved place"))
                .andExpect(jsonPath("$.detail").value("No saved place has the id 7."))
                .andExpect(jsonPath("$.instance").value("/api/v1/saved-places/7"));
    }

    @Test
    void rejectsAnIdThatIsNotANumber() throws Exception {
        mockMvc.perform(delete("/api/v1/saved-places/home"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(savedPlaces);
    }

    /** A place as the database would hand it back. Only the database gives out ids, so the test sets one by force. */
    private static SavedPlace saved(long id, String name, double latitude, double longitude) {
        SavedPlace place = new SavedPlace(name, latitude, longitude);
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }
}
