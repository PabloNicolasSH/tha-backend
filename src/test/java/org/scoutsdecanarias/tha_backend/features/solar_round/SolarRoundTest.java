package org.scoutsdecanarias.tha_backend.features.solar_round;

import org.junit.jupiter.api.Test;
import org.scoutsdecanarias.tha_backend.core.time.ClockConfig;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SolarRoundTest {

    private static SolarRound of(int y, int m, int d) {
        return SolarRound.of(LocalDate.of(y, m, d));
    }

    @Test
    void el30DeSeptiembreEsDeLaRondaQueTermina() {
        SolarRound round = of(2027, 9, 30);
        assertThat(round.label()).isEqualTo("2026-2027");
        assertThat(round.end()).isEqualTo(LocalDate.of(2027, 9, 30));
    }

    @Test
    void el1DeOctubreEsDeLaNueva() {
        SolarRound round = of(2026, 10, 1);
        assertThat(round.label()).isEqualTo("2026-2027");
        assertThat(round.start()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(round.end()).isEqualTo(LocalDate.of(2027, 9, 30));
    }

    @Test
    void el1DeOctubreCambiaDeRonda() {
        assertThat(of(2026, 9, 30).label()).isEqualTo("2025-2026");
        assertThat(of(2026, 10, 1).label()).isEqualTo("2026-2027");
    }

    @Test
    void finDeAnoYPrincipioDeAnoPertenecenALaMismaRonda() {
        assertThat(of(2026, 12, 31).label()).isEqualTo("2026-2027");
        assertThat(of(2027, 1, 1).label()).isEqualTo("2026-2027");
    }

    @Test
    void anoBisiestoNoAlteraLasFechas() {
        SolarRound round = of(2028, 2, 29);
        assertThat(round.label()).isEqualTo("2027-2028");
        assertThat(round.start()).isEqualTo(LocalDate.of(2027, 10, 1));
        assertThat(round.end()).isEqualTo(LocalDate.of(2028, 9, 30));
    }

    @Test
    void laSiguienteEmpiezaElDiaDespuesDelFin() {
        SolarRound next = of(2026, 11, 5).next();
        assertThat(next.label()).isEqualTo("2027-2028");
        assertThat(next.start()).isEqualTo(LocalDate.of(2027, 10, 1));
    }

    @Test
    void hoySeCalculaEnLaZonaDeCanarias() {
        // 30 de septiembre, 23:30 UTC: en Canarias (UTC+1 en verano) ya es 1 de octubre, 00:30
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T23:30:00Z"), ClockConfig.ZONE);
        SolarRoundService service = new SolarRoundService(clock);
        assertThat(service.today()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(service.current().label()).isEqualTo("2026-2027");
        assertThat(service.next().label()).isEqualTo("2027-2028");
    }

    @Test
    void laMismaHoraEnUtcSeriaOtraRonda() {
        Clock utc = Clock.fixed(Instant.parse("2026-09-30T23:30:00Z"), ZoneOffset.UTC);
        assertThat(new SolarRoundService(utc).current().label()).isEqualTo("2025-2026");
    }
}
