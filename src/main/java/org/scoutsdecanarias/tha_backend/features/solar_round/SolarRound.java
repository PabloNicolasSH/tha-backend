package org.scoutsdecanarias.tha_backend.features.solar_round;

import java.time.LocalDate;
import java.time.Month;

/**
 * Ronda solar: el curso scout, del 1 de octubre al 30 de septiembre. No se guarda, se calcula.
 *
 * @param start primer día (1 de octubre)
 * @param end   último día (30 de septiembre del año siguiente)
 * @param label etiqueta, por ejemplo {@code 2026-2027}
 */
public record SolarRound(LocalDate start, LocalDate end, String label) {

    /** La ronda a la que pertenece la fecha: el 30 de septiembre es de la que termina y el 1 de octubre, de la nueva. */
    public static SolarRound of(LocalDate date) {
        int startYear = date.getMonthValue() >= Month.OCTOBER.getValue() ? date.getYear() : date.getYear() - 1;
        LocalDate start = LocalDate.of(startYear, Month.OCTOBER, 1);
        LocalDate end = LocalDate.of(startYear + 1, Month.SEPTEMBER, 30);
        return new SolarRound(start, end, startYear + "-" + (startYear + 1));
    }

    /** La ronda siguiente a esta. */
    public SolarRound next() {
        return of(end.plusDays(1));
    }
}
