package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.cars.CarRegister;
import com.solvelify.routeplanner.cars.CarVariant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The petrol cars of NZTA's Motor Vehicle Register, for a driver to find theirs in four steps:
 * the make, the model, the year, then the engine. Each step answers with the choices for the
 * next, given the ones made so far, and a choice the register does not know gets an empty list.
 */
@RestController
@RequestMapping("/api/v1/cars")
public class CarController {

    private final CarRegister register;

    public CarController(CarRegister register) {
        this.register = register;
    }

    @GetMapping("/makes")
    public List<String> makes() {
        return register.makes();
    }

    @GetMapping("/models")
    public List<String> models(@RequestParam String make) {
        return register.models(make);
    }

    @GetMapping("/years")
    public List<Integer> years(@RequestParam String make, @RequestParam String model) {
        return register.years(make, model);
    }

    /** Each with the register's figure, and how many such cars are registered. */
    @GetMapping("/variants")
    public List<CarVariant> variants(@RequestParam String make, @RequestParam String model, @RequestParam int year) {
        return register.variants(make, model, year);
    }
}
