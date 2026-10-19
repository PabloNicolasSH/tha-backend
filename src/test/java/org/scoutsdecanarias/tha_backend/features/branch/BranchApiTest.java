package org.scoutsdecanarias.tha_backend.features.branch;

import org.junit.jupiter.api.Test;
import org.scoutsdecanarias.tha_backend.AbstractIntegrationTest;
import org.scoutsdecanarias.tha_backend.features.user.Role;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BranchApiTest extends AbstractIntegrationTest {

    @Autowired BranchRepository branches;

    @Test
    void laMigracionDejaLasCincoRamasEnOrden() {
        assertThat(branches.findByActiveTrueOrderBySortOrderAsc())
                .extracting(Branch::getCode)
                .containsExactly("CASTORES", "LOBATOS", "SCOUTS", "ESCULTAS", "ROVERS");
    }

    @Test
    void sinSesionDevuelve401() throws Exception {
        mvc.perform(get("/api/branches")).andExpect(status().isUnauthorized());
    }

    @Test
    void conSesionDevuelveLasCincoEnOrden() throws Exception {
        activeUser("rama-lectura@example.org", Set.of(Role.MEMBER));
        String token = accessToken("rama-lectura@example.org");

        mvc.perform(get("/api/branches").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].code").value("CASTORES"))
                .andExpect(jsonPath("$[0].name").value("Castores"))
                .andExpect(jsonPath("$[4].code").value("ROVERS"));
    }

    @Test
    void laListaPublicaParaElRegistroNoNecesitaSesion() throws Exception {
        mvc.perform(get("/api/branches/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].name").value("Castores"))
                .andExpect(jsonPath("$[0].sortOrder").doesNotExist());
    }

    @Test
    void lasRamasSonFijasNoSePuedenCrearNiBorrar() throws Exception {
        activeUser("rama-admin@example.org", Set.of(Role.ADMIN));
        String token = accessToken("rama-admin@example.org");

        mvc.perform(post("/api/branches").header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"name\":\"Nueva\"}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/branches/1").header("Authorization", bearer(token)))
                .andExpect(status().is4xxClientError());
    }
}
