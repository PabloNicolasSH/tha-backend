package org.scoutsdecanarias.tha_backend.features.auth;

import org.junit.jupiter.api.Test;
import org.scoutsdecanarias.tha_backend.AbstractIntegrationTest;
import org.scoutsdecanarias.tha_backend.features.branch.Branch;
import org.scoutsdecanarias.tha_backend.features.branch.BranchRepository;
import org.scoutsdecanarias.tha_backend.features.scout_group.ScoutGroup;
import org.scoutsdecanarias.tha_backend.features.scout_group.ScoutGroupRepository;
import org.scoutsdecanarias.tha_backend.features.user.Role;
import org.scoutsdecanarias.tha_backend.features.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F0-08: grupo y rama al solicitar la cuenta, y corrección del grupo antes de aprobar. */
class RegisterGroupBranchTest extends AbstractIntegrationTest {

    @Autowired ScoutGroupRepository groups;
    @Autowired BranchRepository branches;

    private ScoutGroup group(String name, boolean active) {
        ScoutGroup g = new ScoutGroup();
        g.setName(name + " " + UUID.randomUUID());
        g.setActive(active);
        return groups.save(g);
    }

    private Branch branch(String code) {
        return branches.findAll().stream().filter(b -> b.getCode().equals(code)).findFirst().orElseThrow();
    }

    private ResultActions register(String email, Long groupId, Long branchId) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"name\":\"Ana\",\"surname\":\"Prueba\""
                + ",\"scoutGroupId\":" + groupId + ",\"branchId\":" + branchId + "}";
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String email() {
        return "solicitud-" + UUID.randomUUID() + "@example.org";
    }

    private String token(Role role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.org";
        activeUser(email, Set.of(role));
        return bearer(accessToken(email));
    }

    private ResultActions changeGroup(Long requestId, Long groupId, String token) throws Exception {
        return mvc.perform(put("/api/admin/account-requests/" + requestId + "/group").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"scoutGroupId\":" + groupId + "}"));
    }

    // ---------- Solicitud con grupo y rama ----------

    @Test
    @Transactional // la persona se lee con sus relaciones perezosas
    void guardaElGrupoYLaRamaIndicados() throws Exception {
        ScoutGroup g = group("Grupo", true);
        Branch castores = branch("CASTORES");
        String email = email();

        register(email, g.getId(), castores.getId()).andExpect(status().isAccepted());

        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getScoutGroup().getId()).isEqualTo(g.getId());
        assertThat(user.getBranch().getCode()).isEqualTo("CASTORES");
    }

    @Test
    void grupoYRamaSonOpcionales() throws Exception {
        String email = email();
        register(email, null, null).andExpect(status().isAccepted());
        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getScoutGroup()).isNull();
        assertThat(user.getBranch()).isNull();
    }

    @Test
    void rechazaGrupoOramaInexistentesOGrupoDesactivado() throws Exception {
        register(email(), 999_999L, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El grupo indicado no existe"));
        register(email(), null, 999_999L).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La rama indicada no existe"));

        ScoutGroup off = group("Desactivado", false);
        String email = email();
        register(email, off.getId(), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El grupo indicado ya no admite solicitudes"));
        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
    }

    // ---------- Bandeja de solicitudes ----------

    @Test
    void laBandejaMuestraGrupoYRama() throws Exception {
        ScoutGroup g = group("Visible", true);
        String email = email();
        register(email, g.getId(), branch("ROVERS").getId()).andExpect(status().isAccepted());

        mvc.perform(get("/api/admin/account-requests").header("Authorization", token(Role.SECRETARY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='" + email + "')].scoutGroupName").value(g.getName()))
                .andExpect(jsonPath("$[?(@.email=='" + email + "')].scoutGroupId").value(g.getId().intValue()))
                .andExpect(jsonPath("$[?(@.email=='" + email + "')].branchName").value("Rovers"));
    }

    // ---------- Corregir el grupo antes de aprobar ----------

    @Test
    void laSecretariaCorrigeElGrupoAntesDeAprobar() throws Exception {
        ScoutGroup declared = group("Declarado", true);
        ScoutGroup right = group("Correcto", true);
        String email = email();
        register(email, declared.getId(), null).andExpect(status().isAccepted());
        Long id = users.findByEmailIgnoreCase(email).orElseThrow().getId();
        String secretary = token(Role.SECRETARY);

        changeGroup(id, right.getId(), secretary).andExpect(status().isOk())
                .andExpect(jsonPath("$.scoutGroupName").value(right.getName()));
        assertThat(users.findByEmailIgnoreCase(email).orElseThrow().getScoutGroup().getId()).isEqualTo(right.getId());

        // También se puede dejar sin grupo ("Ninguno (federación)")
        changeGroup(id, null, secretary).andExpect(status().isOk()).andExpect(jsonPath("$.scoutGroupId").doesNotExist());
        assertThat(users.findByEmailIgnoreCase(email).orElseThrow().getScoutGroup()).isNull();
    }

    @Test
    void laCorreccionRechazaGruposInexistentesODesactivadosYSoloVaAntesDeAprobar() throws Exception {
        String email = email();
        register(email, null, null).andExpect(status().isAccepted());
        Long id = users.findByEmailIgnoreCase(email).orElseThrow().getId();
        String admin = token(Role.ADMIN);

        changeGroup(id, 999_999L, admin).andExpect(status().isBadRequest());
        changeGroup(id, group("Apagado", false).getId(), admin).andExpect(status().isBadRequest());
        changeGroup(999_999L, null, admin).andExpect(status().isNotFound());

        mvc.perform(post("/api/admin/account-requests/" + id + "/approve").header("Authorization", admin))
                .andExpect(status().isOk());
        changeGroup(id, group("Tarde", true).getId(), admin).andExpect(status().isConflict());
    }

    @Test
    void sinLosRolesDeLaFederacionNoSePuedeCorregir() throws Exception {
        String email = email();
        register(email, null, null).andExpect(status().isAccepted());
        Long id = users.findByEmailIgnoreCase(email).orElseThrow().getId();

        mvc.perform(put("/api/admin/account-requests/" + id + "/group").contentType(MediaType.APPLICATION_JSON)
                .content("{\"scoutGroupId\":null}")).andExpect(status().isUnauthorized());
        for (Role role : Set.of(Role.MEMBER, Role.TREASURER, Role.GROUP_COORDINATOR)) {
            changeGroup(id, null, token(role)).andExpect(status().isForbidden());
        }
        changeGroup(id, null, token(Role.ADMIN)).andExpect(status().isOk());
    }
}
