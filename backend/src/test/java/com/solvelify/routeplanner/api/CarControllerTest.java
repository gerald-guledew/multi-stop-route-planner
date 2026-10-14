package com.solvelify.routeplanner.api;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solvelify.routeplanner.cars.Car;
import com.solvelify.routeplanner.cars.CarRegister;
import com.solvelify.routeplanner.cars.CarVariant;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CarController.class)
class CarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CarRegister register;

    @Test
    void answersWithTheMakes() throws Exception {
        given(register.makes()).willReturn(List.of("MAZDA", "SUZUKI"));

        mockMvc.perform(get("/api/v1/cars/makes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("MAZDA"))
                .andExpect(jsonPath("$[1]").value("SUZUKI"));
    }

    @Test
    void answersWithTheModelsOfTheMakeAskedFor() throws Exception {
        given(register.models("LAND ROVER")).willReturn(List.of("DEFENDER", "DISCOVERY"));

        mockMvc.perform(get("/api/v1/cars/models").param("make", "LAND ROVER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1]").value("DISCOVERY"));
    }

    @Test
    void answersWithTheYearsOfTheModelAskedFor() throws Exception {
        given(register.years("SUZUKI", "SWIFT")).willReturn(List.of(2018, 2017));

        mockMvc.perform(get("/api/v1/cars/years").param("make", "SUZUKI").param("model", "SWIFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(2018));
    }

    @Test
    void answersWithEachEngineAndItsFigure() throws Exception {
        Car usedSwift = new Car("SUZUKI", "SWIFT", 2018, new BigDecimal("1.2"), true);
        given(register.variants("SUZUKI", "SWIFT", 2018)).willReturn(List.of(new CarVariant(usedSwift, 4.2, 552)));

        mockMvc.perform(get("/api/v1/cars/variants").param("make", "SUZUKI").param("model", "SWIFT").param("year", "2018"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].car.make").value("SUZUKI"))
                .andExpect(jsonPath("$[0].car.model").value("SWIFT"))
                .andExpect(jsonPath("$[0].car.year").value(2018))
                .andExpect(jsonPath("$[0].car.engineLitres").value(1.2))
                .andExpect(jsonPath("$[0].car.importedUsed").value(true))
                .andExpect(jsonPath("$[0].litresPer100Km").value(4.2))
                .andExpect(jsonPath("$[0].cars").value(552));
    }

    @Test
    void refusesToListModelsWithoutAMake() throws Exception {
        mockMvc.perform(get("/api/v1/cars/models"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(register);
    }
}
