package com.solvelify.routeplanner.cars;

import java.math.BigDecimal;

/**
 * A car as NZTA's Motor Vehicle Register describes it, and as a driver would: its make, model
 * and year, its engine size, and whether it was sold new in New Zealand or imported used. The
 * last matters because a used import keeps the fuel figure of the country it came from.
 *
 * @param make         as the register writes it, such as "SUZUKI"
 * @param model        as the register writes it, such as "SWIFT"
 * @param engineLitres to the nearest tenth of a litre, such as 1.2
 * @param importedUsed true for a used import, false for a car sold new here
 */
public record Car(String make, String model, int year, BigDecimal engineLitres, boolean importedUsed) {
}
