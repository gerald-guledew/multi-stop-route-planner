package com.solvelify.routeplanner.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solvelify.routeplanner.distance.GroundHeights;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HeightController.class)
class HeightControllerTest {

    private static final String MAUNGAWHAU = """
            { "latitude": -36.8778, "longitude": 174.7639 }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroundHeights groundHeights;

    @Test
    void givesTheHeightInWholeMetres() throws Exception {
        given(groundHeights.at(anyDouble(), anyDouble())).willReturn(OptionalDouble.of(194.6));

        mockMvc.perform(post("/api/v1/heights/lookup").contentType(MediaType.APPLICATION_JSON).content(MAUNGAWHAU))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heightMetres").value(195));
    }

    @Test
    void asksForTheHeightOfThePointSent() throws Exception {
        given(groundHeights.at(anyDouble(), anyDouble())).willReturn(OptionalDouble.of(195));

        mockMvc.perform(post("/api/v1/heights/lookup").contentType(MediaType.APPLICATION_JSON).content(MAUNGAWHAU))
                .andExpect(status().isOk());

        verify(groundHeights).at(-36.8778, 174.7639);
    }

    @Test
    void givesNoughtForTheSea() throws Exception {
        given(groundHeights.at(anyDouble(), anyDouble())).willReturn(OptionalDouble.of(0.2));

        mockMvc.perform(post("/api/v1/heights/lookup").contentType(MediaType.APPLICATION_JSON).content(MAUNGAWHAU))
                .andExpect(jsonPath("$.heightMetres").value(0));
    }

    @Test
    void givesNullAndNotNoughtWhereNothingIsKnown() throws Exception {
        given(groundHeights.at(anyDouble(), anyDouble())).willReturn(OptionalDouble.empty());

        mockMvc.perform(post("/api/v1/heights/lookup").contentType(MediaType.APPLICATION_JSON).content(MAUNGAWHAU))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heightMetres").value(nullValue()));
    }

    @Test
    void refusesAPointWithNoLatitude() throws Exception {
        mockMvc.perform(post("/api/v1/heights/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"longitude\": 174.7639 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("latitude"));
    }

    @Test
    void refusesALongitudeOffTheGlobe() throws Exception {
        mockMvc.perform(post("/api/v1/heights/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"latitude\": -36.8778, \"longitude\": 274.7639 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value(containsString("between -180 and 180")));
    }
}
