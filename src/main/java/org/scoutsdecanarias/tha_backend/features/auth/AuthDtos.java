package org.scoutsdecanarias.tha_backend.features.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.scoutsdecanarias.tha_backend.features.user.Role;
import org.scoutsdecanarias.tha_backend.features.user.User;

import java.util.Set;

public final class AuthDtos {

    private AuthDtos() {
    }

    /** {@code rememberMe}: "Mantener sesión iniciada". Si falta, se considera false. */
    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            Boolean rememberMe) {

        public boolean remember() {
            return Boolean.TRUE.equals(rememberMe);
        }
    }

    /** La cuenta se solicita sin contraseña: la crea la persona con el enlace que recibe al ser aprobada. */
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 160) String surname,
            Long scoutGroupId,
            Long branchId) {
    }

    public record SetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 10, max = 100, message = "La contraseña debe tener al menos 10 caracteres") String password) {
    }

    public record UserDto(Long id, String email, String name, String surname, Set<Role> roles, Long scoutGroupId) {

        /** {@code roles}: los efectivos hoy (los guardados más los derivados de puestos de rama vigentes). */
        public static UserDto from(User user, Set<Role> roles) {
            Long groupId = user.getScoutGroup() == null ? null : user.getScoutGroup().getId();
            return new UserDto(user.getId(), user.getEmail(), user.getName(), user.getSurname(), roles, groupId);
        }
    }

    /** Respuesta de login y refresh. El refresh token NO va aquí: viaja en una cookie HttpOnly. */
    public record AuthResponse(String accessToken, long expiresInSeconds, UserDto user) {
    }
}
