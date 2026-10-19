package org.scoutsdecanarias.tha_backend.features.solar_round;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/solar-rounds")
@RequiredArgsConstructor
public class SolarRoundController {

    private final SolarRoundService service;

    @GetMapping("/current")
    public SolarRound current() {
        return service.current();
    }

    @GetMapping("/next")
    public SolarRound next() {
        return service.next();
    }
}
