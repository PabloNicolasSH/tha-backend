package org.scoutsdecanarias.tha_backend.features.scout_group;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.scoutsdecanarias.tha_backend.AbstractIntegrationTest;
import org.scoutsdecanarias.tha_backend.features.user.Role;
import org.scoutsdecanarias.tha_backend.features.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScoutGroupApiTest extends AbstractIntegrationTest {

    @Autowired ScoutGroupRepository groups;

    private String tokenFor(Role role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.org";
        activeUser(email, Set.of(role));
        return bearer(accessToken(email));
    }

    private ResultActions save(String method, String url, String token, String name, String email, String island)
            throws Exception {
        String body = "{\"name\":\"" + name + "\",\"email\":" + (email == null ? "null" : "\"" + email + "\"")
                + ",\"island\":" + (island == null ? "null" : "\"" + island + "\"") + "}";
        var request = method.equals("POST") ? post(url) : put(url);
        return mvc.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long create(String token, String name) throws Exception {
        MvcResult result = save("POST", "/api/admin/scout-groups", token, name, null, null)
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    // ---------- Permisos: matriz de roles ----------

    @Test
    void soloAdminYSecretariaGestionanLosGrupos() throws Exception {
        mvc.perform(get("/api/admin/scout-groups")).andExpect(status().isUnauthorized());

        for (Role role : Set.of(Role.MEMBER, Role.TREASURER, Role.GROUP_COORDINATOR)) {
            String token = tokenFor(role);
            mvc.perform(get("/api/admin/scout-groups").header("Authorization", token)).andExpect(status().isForbidden());
            save("POST", "/api/admin/scout-groups", token, "No debe crearse " + role, null, null)
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/admin/scout-groups/1").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }
        for (Role role : Set.of(Role.ADMIN, Role.SECRETARY)) {
            mvc.perform(get("/api/admin/scout-groups").header("Authorization", tokenFor(role))).andExpect(status().isOk());
        }
    }

    // ---------- Alta, edición y validación ----------

    @Test
    void creaYEditaUnGrupo() throws Exception {
        String token = tokenFor(Role.SECRETARY);
        MvcResult created = save("POST", "/api/admin/scout-groups", token, "  Grupo Teide ", "teide@example.org", "TENERIFE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Grupo Teide"))
                .andExpect(jsonPath("$.island").value("TENERIFE"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.members").value(0))
                .andReturn();
        long id = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();

        save("PUT", "/api/admin/scout-groups/" + id, token, "Grupo Teide Norte", "", "LA_PALMA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Grupo Teide Norte"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.island").value("LA_PALMA"));
        // Se puede guardar con su propio nombre
        save("PUT", "/api/admin/scout-groups/" + id, token, "Grupo Teide Norte", null, null).andExpect(status().isOk());
        save("PUT", "/api/admin/scout-groups/999999", token, "Nadie", null, null).andExpect(status().isNotFound());
    }

    @Test
    void elNombreEsUnicoSinDistinguirMayusculas() throws Exception {
        String token = tokenFor(Role.ADMIN);
        long id = create(token, "Grupo Unico");
        save("POST", "/api/admin/scout-groups", token, "GRUPO UNICO", null, null).andExpect(status().isConflict());
        long other = create(token, "Grupo Otro");
        save("PUT", "/api/admin/scout-groups/" + other, token, "grupo unico", null, null).andExpect(status().isConflict());
        assertThat(id).isNotEqualTo(other);
    }

    @Test
    void validaNombreEmailEIsla() throws Exception {
        String token = tokenFor(Role.ADMIN);
        save("POST", "/api/admin/scout-groups", token, "   ", null, null).andExpect(status().isBadRequest());
        save("POST", "/api/admin/scout-groups", token, "Grupo Email Malo", "no-es-un-email", null)
                .andExpect(status().isBadRequest());
        save("POST", "/api/admin/scout-groups", token, "Grupo Isla Mala", null, "ATLANTIDA")
                .andExpect(status().isBadRequest());
        assertThat(groups.existsByNameIgnoreCase("Grupo Isla Mala")).isFalse();
    }

    @Test
    void ofreceLaListaFijaDeIslas() throws Exception {
        mvc.perform(get("/api/admin/scout-groups/islands").header("Authorization", tokenFor(Role.SECRETARY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[?(@.code=='GRAN_CANARIA')].name").value("Gran Canaria"));
    }

    // ---------- Desactivar, borrar y endpoint público ----------

    @Test
    void desactivarYReactivarCambiaLoQueVeElRegistro() throws Exception {
        String token = tokenFor(Role.ADMIN);
        long id = create(token, "Grupo Publico Visible");

        mvc.perform(get("/api/scout-groups/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Grupo Publico Visible')]").exists());

        mvc.perform(put("/api/admin/scout-groups/" + id + "/active").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/scout-groups/public"))
                .andExpect(jsonPath("$[?(@.name=='Grupo Publico Visible')]").doesNotExist());

        mvc.perform(put("/api/admin/scout-groups/" + id + "/active").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/scout-groups/public"))
                .andExpect(jsonPath("$[?(@.name=='Grupo Publico Visible')]").exists());
    }

    @Test
    void elEndpointPublicoNoExponeEmailNiIsla() throws Exception {
        String token = tokenFor(Role.ADMIN);
        save("POST", "/api/admin/scout-groups", token, "Grupo Privado", "privado@example.org", "LANZAROTE")
                .andExpect(status().isCreated());
        MvcResult result = mvc.perform(get("/api/scout-groups/public")).andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Grupo Privado").doesNotContain("privado@example.org").doesNotContain("LANZAROTE");
        java.util.List<java.util.Map<String, Object>> found = JsonPath.read(body, "$[?(@.name=='Grupo Privado')]");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).keySet()).containsExactlyInAnyOrder("id", "name");
    }

    @Test
    void conPersonasNoSeBorraSoloSeDesactiva() throws Exception {
        String token = tokenFor(Role.ADMIN);
        long id = create(token, "Grupo Con Gente");
        User member = activeUser("miembro-" + UUID.randomUUID() + "@example.org", Set.of(Role.MEMBER));
        member.setScoutGroup(groups.findById(id).orElseThrow());
        users.save(member);

        mvc.perform(get("/api/admin/scout-groups").header("Authorization", token))
                .andExpect(jsonPath("$[?(@.name=='Grupo Con Gente')].members").value(1));
        mvc.perform(delete("/api/admin/scout-groups/" + id).header("Authorization", token))
                .andExpect(status().isConflict());
        assertThat(groups.existsById(id)).isTrue();

        // Desactivado conserva a sus personas
        mvc.perform(put("/api/admin/scout-groups/" + id + "/active").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk());
        assertThat(users.countByScoutGroupId(id)).isEqualTo(1);
    }

    @Test
    void sinPersonasSePuedeBorrar() throws Exception {
        String token = tokenFor(Role.SECRETARY);
        long id = create(token, "Grupo Vacio");
        mvc.perform(delete("/api/admin/scout-groups/" + id).header("Authorization", token))
                .andExpect(status().isNoContent());
        assertThat(groups.existsById(id)).isFalse();
        mvc.perform(delete("/api/admin/scout-groups/" + id).header("Authorization", token))
                .andExpect(status().isNotFound());
    }
}
