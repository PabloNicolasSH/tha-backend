package org.scoutsdecanarias.tha_backend.features.solar_round;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class SolarRoundService {

    private final Clock clock;

    /** Hoy, según la zona horaria del reloj (Atlantic/Canary). */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public SolarRound current() {
        return SolarRound.of(today());
    }

    public SolarRound next() {
        return current().next();
    }
}
