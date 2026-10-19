package org.scoutsdecanarias.tha_backend.core.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Reloj de la aplicación, en la zona horaria de Canarias. Los tests lo sustituyen para fijar la fecha. */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Atlantic/Canary");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
