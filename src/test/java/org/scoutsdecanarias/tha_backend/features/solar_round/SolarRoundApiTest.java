package org.scoutsdecanarias.tha_backend.features.solar_round;

import org.junit.jupiter.api.Test;
import org.scoutsdecanarias.tha_backend.AbstractIntegrationTest;
import org.scoutsdecanarias.tha_backend.features.user.Role;

import java.util.Set;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SolarRoundApiTest extends AbstractIntegrationTest {

    @Test
    void sinSesionDevuelve401() throws Exception {
        mvc.perform(get("/api/solar-rounds/current")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/solar-rounds/next")).andExpect(status().isUnauthorized());
    }

    @Test
    void conSesionDevuelveLaVigenteYLaSiguiente() throws Exception {
        activeUser("ronda-lectura@example.org", Set.of(Role.MEMBER));
        String token = accessToken("ronda-lectura@example.org");

        mvc.perform(get("/api/solar-rounds/current").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value(endsWith("-10-01")))
                .andExpect(jsonPath("$.end").value(endsWith("-09-30")))
                .andExpect(jsonPath("$.label").value(matchesPattern("\\d{4}-\\d{4}")));
        mvc.perform(get("/api/solar-rounds/next").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value(endsWith("-10-01")));
    }
}
