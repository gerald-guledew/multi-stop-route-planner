package com.solvelify.routeplanner.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
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

import com.solvelify.routeplanner.cars.Car;
import com.solvelify.routeplanner.cars.CarNotInRegisterException;
import com.solvelify.routeplanner.cars.CarVariant;
import com.solvelify.routeplanner.vehicle.Vehicle;
import com.solvelify.routeplanner.vehicle.VehicleSize;
import com.solvelify.routeplanner.vehicle.Vehicles;
import java.math.BigDecimal;
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

    private static final Car SWIFT = new Car("SUZUKI", "SWIFT", 2018, new BigDecimal("1.2"));

    private static final String THE_SWIFT = """
            { "make": "SUZUKI", "model": "SWIFT", "year": 2018, "engineLitres": 1.2 }
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
                .andExpect(jsonPath("$.figureFrom").value("dashboard"))
                .andExpect(jsonPath("$.size").value("medium"))
                .andExpect(jsonPath("$.car").value(nullValue()));
    }

    @Test
    void givesTheCarFoundInTheRegister() throws Exception {
        given(vehicles.current()).willReturn(Optional.of(new Vehicle(new CarVariant(SWIFT, 4.8, 1896), null)));

        mockMvc.perform(get("/api/v1/vehicle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.litresPer100Km").value(4.8))
                .andExpect(jsonPath("$.figureFrom").value("register"))
                .andExpect(jsonPath("$.size").value(nullValue()))
                .andExpect(jsonPath("$.car.make").value("SUZUKI"))
                .andExpect(jsonPath("$.car.model").value("SWIFT"))
                .andExpect(jsonPath("$.car.year").value(2018))
                .andExpect(jsonPath("$.car.engineLitres").value(1.2))
                .andExpect(jsonPath("$.car.importedUsed").doesNotExist());
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
    void savesACarFoundInTheRegister() throws Exception {
        given(vehicles.saveFromRegister(any(), any())).willReturn(new Vehicle(new CarVariant(SWIFT, 4.8, 1896), null));

        mockMvc.perform(put("/api/v1/vehicle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"car\": " + THE_SWIFT + " }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.figureFrom").value("register"))
                .andExpect(jsonPath("$.car.model").value("SWIFT"));

        verify(vehicles).saveFromRegister(SWIFT, null);
    }

    @Test
    void savesACarFoundInTheRegisterWithItsDashboardsFigure() throws Exception {
        given(vehicles.saveFromRegister(any(), any())).willReturn(new Vehicle(new CarVariant(SWIFT, 4.8, 1896), 5.6));

        mockMvc.perform(put("/api/v1/vehicle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"car\": " + THE_SWIFT + ", \"litresPer100Km\": 5.6 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.litresPer100Km").value(5.6))
                .andExpect(jsonPath("$.figureFrom").value("dashboard"));

        verify(vehicles).saveFromRegister(SWIFT, 5.6);
    }

    @Test
    void refusesAVehicleWithASizeAndNoFigure() throws Exception {
        mockMvc.perform(put("/api/v1/vehicle").contentType(MediaType.APPLICATION_JSON).content("{ \"size\": \"medium\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("litresPer100KmWithSize"))
                .andExpect(jsonPath("$.errors[0].message").value("litresPer100Km is needed with a size"));

        verifyNoInteractions(vehicles);
    }

    @Test
    void refusesAVehicleWithASizeAndACar() throws Exception {
        mockMvc.perform(put("/api/v1/vehicle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"litresPer100Km\": 7.5, \"size\": \"medium\", \"car\": " + THE_SWIFT + " }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sizeOrCar"))
                .andExpect(jsonPath("$.errors[0].message").value("must be a size or a car, one of the two"));

        verifyNoInteractions(vehicles);
    }

    @Test
    void refusesAVehicleWithNeitherASizeNorACar() throws Exception {
        mockMvc.perform(put("/api/v1/vehicle").contentType(MediaType.APPLICATION_JSON).content("{ \"litresPer100Km\": 7.5 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sizeOrCar"));

        verifyNoInteractions(vehicles);
    }

    @Test
    void refusesACarWithoutItsEngine() throws Exception {
        mockMvc.perform(put("/api/v1/vehicle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"car\": { \"make\": \"SUZUKI\", \"model\": \"SWIFT\", \"year\": 2018 } }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("car.engineLitres"));

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
    void saysSoWhenTheRegisterDoesNotListTheCar() throws Exception {
        given(vehicles.saveFromRegister(any(), any())).willThrow(new CarNotInRegisterException(SWIFT));

        mockMvc.perform(put("/api/v1/vehicle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"car\": " + THE_SWIFT + " }"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Car not in the register"))
                .andExpect(jsonPath("$.detail").value("NZTA's register has no 2018 SUZUKI SWIFT 1.2 L."));
    }

    @Test
    void removesTheVehicle() throws Exception {
        mockMvc.perform(delete("/api/v1/vehicle"))
                .andExpect(status().isNoContent());

        verify(vehicles).remove();
    }
}
