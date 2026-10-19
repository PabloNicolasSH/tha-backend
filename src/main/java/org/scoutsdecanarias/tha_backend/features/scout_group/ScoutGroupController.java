package org.scoutsdecanarias.tha_backend.features.scout_group;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
public class ScoutGroupController {

    public record GroupRequest(
            @NotBlank(message = "Indica el nombre del grupo") @Size(max = 160, message = "El nombre es demasiado largo") String name,
            @Email(message = "El email de contacto no es válido") @Size(max = 255) String email,
            @Size(max = 40) String island) {
    }

    public record ActiveRequest(boolean active) {
    }

    public record IslandOption(String code, String name) {
    }

    private final ScoutGroupService service;

    public ScoutGroupController(ScoutGroupService service) {
        this.service = service;
    }

    /** Sin sesión: lo usa el formulario de solicitud de cuenta. Solo identificador y nombre de los grupos activos. */
    @GetMapping("/api/scout-groups/public")
    List<ScoutGroupService.PublicGroup> publicList() {
        return service.activeGroups();
    }

    @GetMapping("/api/admin/scout-groups/islands")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARY')")
    List<IslandOption> islands() {
        return Arrays.stream(Island.values()).map(i -> new IslandOption(i.name(), i.label())).toList();
    }

    @GetMapping("/api/admin/scout-groups")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARY')")
    List<ScoutGroupService.GroupInfo> list() {
        return service.list();
    }

    @PostMapping("/api/admin/scout-groups")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARY')")
    @ResponseStatus(HttpStatus.CREATED)
    ScoutGroupService.GroupInfo create(@Valid @RequestBody GroupRequest request) {
        return service.create(new ScoutGroupService.GroupInput(request.name(), request.email(), request.island()));
    }

    @PutMapping("/api/admin/scout-groups/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARY')")
    ScoutGroupService.GroupInfo update(@PathVariable Long id, @Valid @RequestBody GroupRequest request) {
        return service.update(id, new ScoutGroupService.GroupInput(request.name(), request.email(), request.island()));
    }

    @PutMapping("/api/admin/scout-groups/{id}/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARY')")
    ScoutGroupService.GroupInfo setActive(@PathVariable Long id, @RequestBody ActiveRequest request) {
        return service.setActive(id, request.active());
    }

    @DeleteMapping("/api/admin/scout-groups/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARY')")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
