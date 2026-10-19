package org.scoutsdecanarias.tha_backend.features.branch;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    public record BranchResponse(Long id, String code, String name) {
    }

    private final BranchRepository branches;

    /** Para el formulario de solicitud de cuenta, que aún no tiene sesión: las mismas ramas, sin ningún dato más. */
    @GetMapping("/public")
    public List<BranchResponse> publicList() {
        return list();
    }

    /** Las cinco ramas, en orden. Exige sesión (lo decide la regla general de SecurityConfig). */
    @GetMapping
    public List<BranchResponse> list() {
        return branches.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(b -> new BranchResponse(b.getId(), b.getCode(), b.getName()))
                .toList();
    }
}
