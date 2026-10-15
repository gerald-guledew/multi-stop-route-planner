package com.solvelify.routeplanner.cars;

import java.math.BigDecimal;

/**
 * A car as NZTA's Motor Vehicle Register describes it, and as a driver would: its make, model
 * and year, and its engine size. Whether it was sold new here or imported used is not part of
 * it, see {@link CarRegister}.
 *
 * @param make         as the register writes it, such as "SUZUKI"
 * @param model        as the register writes it, such as "SWIFT"
 * @param engineLitres to the nearest tenth of a litre, such as 1.2
 */
public record Car(String make, String model, int year, BigDecimal engineLitres) {
}
