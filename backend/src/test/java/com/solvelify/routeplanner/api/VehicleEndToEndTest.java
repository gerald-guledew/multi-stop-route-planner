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
import org.springframework.jdbc.core.simple.JdbcClient;
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

    @Autowired
    private JdbcClient jdbc;

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

    @Test
    void aCarIsFoundInTheRegisterAndSaved() throws Exception {
        jdbc.sql("DELETE FROM car_variant").update();
        jdbc.sql("""
                INSERT INTO car_variant (make, model, year, engine_litres, imported_used, cars, litres_per_100_km)
                VALUES ('SUZUKI', 'SWIFT', 2018, 1.2, false, 1344, 4.8)
                """).update();
        try {
            String car = mockMvc.perform(get("/api/v1/cars/variants")
                            .param("make", "SUZUKI").param("model", "SWIFT").param("year", "2018"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].litresPer100Km").value(4.8))
                    .andReturn().getResponse().getContentAsString()
                    .replaceAll("^\\[\\{\"car\":(\\{[^}]*\\}).*$", "$1");

            // The car is sent back as the list gave it.
            mockMvc.perform(put("/api/v1/vehicle")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"car\": " + car + " }"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/vehicle"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.litresPer100Km").value(4.8))
                    .andExpect(jsonPath("$.figureFrom").value("register"))
                    .andExpect(jsonPath("$.car.model").value("SWIFT"))
                    .andExpect(jsonPath("$.car.importedUsed").value(false));
        } finally {
            jdbc.sql("DELETE FROM car_variant").update();
        }
    }
}
