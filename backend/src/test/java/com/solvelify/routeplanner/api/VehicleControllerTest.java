package com.solvelify.routeplanner.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solvelify.routeplanner.vehicle.Vehicle;
import com.solvelify.routeplanner.vehicle.VehicleSize;
import com.solvelify.routeplanner.vehicle.Vehicles;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VehicleController.class)
class VehicleControllerTest {

    private static final String A_MEDIUM_CAR = """
            { "litresPer100Km": 7.5, "size": "medium" }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Vehicles vehicles;

    @Test
    void answersWithNoContentUntilAVehicleIsSaved() throws Exception {
        given(vehicles.current()).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/vehicle"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void givesTheSavedVehicle() throws Exception {
        given(vehicles.current()).willReturn(Optional.of(new Vehicle(7.5, VehicleSize.MEDIUM)));

        mockMvc.perform(get("/api/v1/vehicle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.litresPer100Km").value(7.5))
                .andExpect(jsonPath("$.size").value("medium"));
    }

    @Test
    void asksNothingInBetweenToKeepACopy() throws Exception {
        given(vehicles.current()).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/vehicle"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void savesTheVehicleSent() throws Exception {
        given(vehicles.save(anyDouble(), any())).willReturn(new Vehicle(7.5, VehicleSize.MEDIUM));

        mockMvc.perform(put("/api/v1/vehicle").contentType(MediaType.APPLICATION_JSON).content(A_MEDIUM_CAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.litresPer100Km").value(7.5))
                .andExpect(jsonPath("$.size").value("medium"));

        verify(vehicles).save(7.5, VehicleSize.MEDIUM);
    }

    @Test
    void refusesAVehicleWithNoFigure() throws Exception {
        mockMvc.perform(put("/api/v1/vehicle").contentType(MediaType.APPLICATION_JSON).content("{ \"size\": \"medium\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("litresPer100Km"));

        verifyNoInteractions(vehicles);
    }

    @Test
    void refusesAFigureNoPetrolCarShows() throws Exception {
        mockMvc.perform(put("/api/v1/vehicle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"litresPer100Km\": 75, \"size\": \"medium\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("must be between 2 and 30"));
    }

    @Test
    void refusesASizeItDoesNotKnow() throws Exception {
        mockMvc.perform(put("/api/v1/vehicle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"litresPer100Km\": 7.5, \"size\": \"Medium\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].message").value("must be small, medium or large"));
    }

    @Test
    void removesTheVehicle() throws Exception {
        mockMvc.perform(delete("/api/v1/vehicle"))
                .andExpect(status().isNoContent());

        verify(vehicles).remove();
    }
}
