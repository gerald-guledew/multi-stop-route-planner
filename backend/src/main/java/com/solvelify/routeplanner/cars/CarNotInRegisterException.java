package com.solvelify.routeplanner.cars;

import java.math.RoundingMode;

/** A car asked for by its description that the register does not list. */
public class CarNotInRegisterException extends RuntimeException {

    public CarNotInRegisterException(Car car) {
        super("NZTA's register has no " + car.year() + " " + car.make() + " " + car.model() + " "
                + car.engineLitres().setScale(1, RoundingMode.HALF_UP).toPlainString() + " L "
                + (car.importedUsed() ? "imported used." : "sold new in New Zealand."));
    }
}
