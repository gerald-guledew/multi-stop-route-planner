package com.solvelify.routeplanner.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solvelify.routeplanner.vehicle.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The vehicle through the whole application, from JSON in to the table and back, with nothing
 * mocked. The requests are the ones in docs/api.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VehicleEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VehicleRepository repository;

    @BeforeEach
    void emptyTheTable() {
        repository.deleteAllInBatch();
    }

    @Test
    void aVehicleIsSavedChangedAndRemoved() throws Exception {
        mockMvc.perform(get("/api/v1/vehicle"))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/v1/vehicle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"litresPer100Km\": 7.5, \"size\": \"medium\" }"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/vehicle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"litresPer100Km\": 6.1, \"size\": \"small\" }"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/vehicle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.litresPer100Km").value(6.1))
                .andExpect(jsonPath("$.size").value("small"));

        mockMvc.perform(delete("/api/v1/vehicle"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/vehicle"))
                .andExpect(status().isNoContent());
    }
}
