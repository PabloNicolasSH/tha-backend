package org.scoutsdecanarias.tha_backend.features.user;

import org.scoutsdecanarias.tha_backend.core.exception.ApiException;
import org.scoutsdecanarias.tha_backend.core.mail.MailService;
import org.scoutsdecanarias.tha_backend.features.auth.InviteProperties;
import org.scoutsdecanarias.tha_backend.features.auth.PasswordSetupTokenService;
import org.scoutsdecanarias.tha_backend.features.scout_group.ScoutGroup;
import org.scoutsdecanarias.tha_backend.features.scout_group.ScoutGroupRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/** Gestión de solicitudes de cuenta por la federación (secretaría / administración). */
@Service
public class UserAdminService {

    public record RequestSummary(Long id, String email, String name, String surname, UserStatus status,
                                 Long scoutGroupId, String scoutGroupName, String branchName, Instant createdAt) {

        static RequestSummary from(User u) {
            return new RequestSummary(u.getId(), u.getEmail(), u.getName(), u.getSurname(), u.getStatus(),
                    u.getScoutGroup() == null ? null : u.getScoutGroup().getId(),
                    u.getScoutGroup() == null ? null : u.getScoutGroup().getName(),
                    u.getBranch() == null ? null : u.getBranch().getName(), u.getCreatedAt());
        }
    }

    private final UserRepository users;
    private final ScoutGroupRepository groups;
    private final PasswordSetupTokenService setupTokens;
    private final MailService mail;
    private final InviteProperties inviteProperties;
    private final String frontendUrl;

    public UserAdminService(UserRepository users, ScoutGroupRepository groups, PasswordSetupTokenService setupTokens, MailService mail,
                            InviteProperties inviteProperties, @Value("${tha.frontend-url}") String frontendUrl) {
        this.users = users;
        this.groups = groups;
        this.setupTokens = setupTokens;
        this.mail = mail;
        this.inviteProperties = inviteProperties;
        this.frontendUrl = frontendUrl;
    }

    /** Solicitudes abiertas: pendientes de aprobar y aprobadas que aún no han creado su contraseña. */
    @Transactional(readOnly = true)
    public List<RequestSummary> openRequests() {
        return users.findByStatusInOrderByCreatedAtAsc(EnumSet.of(UserStatus.PENDING, UserStatus.INVITED))
                .stream().map(RequestSummary::from).toList();
    }

    /** Corrige el grupo que la persona declaró (o lo quita) mientras la solicitud está pendiente. */
    @Transactional
    public RequestSummary changeGroup(Long id, Long groupId) {
        User user = find(id);
        if (user.getStatus() != UserStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "El grupo solo se corrige antes de aprobar la solicitud");
        }
        if (groupId == null) {
            user.setScoutGroup(null);
        } else {
            ScoutGroup group = groups.findById(groupId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "El grupo indicado no existe"));
            if (!group.isActive()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "El grupo indicado está desactivado");
            }
            user.setScoutGroup(group);
        }
        return RequestSummary.from(user);
    }

    @Transactional
    public RequestSummary approve(Long id) {
        User user = find(id);
        if (user.getStatus() != UserStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Esta solicitud ya no está pendiente");
        }
        user.setStatus(UserStatus.INVITED);
        sendInvitation(user);
        return RequestSummary.from(user);
    }

    /** Genera un enlace nuevo (el anterior deja de valer). Útil si caducó o el servidor se reinició. */
    @Transactional
    public RequestSummary resendInvitation(Long id) {
        User user = find(id);
        if (user.getStatus() != UserStatus.INVITED) {
            throw new ApiException(HttpStatus.CONFLICT, "Solo se puede reenviar a cuentas aprobadas sin contraseña");
        }
        sendInvitation(user);
        return RequestSummary.from(user);
    }

    /** Rechaza una solicitud abierta. No hay nada más que conservar: se elimina. */
    @Transactional
    public void reject(Long id) {
        User user = find(id);
        if (user.getStatus() != UserStatus.PENDING && user.getStatus() != UserStatus.INVITED) {
            throw new ApiException(HttpStatus.CONFLICT, "Solo se pueden rechazar solicitudes abiertas");
        }
        setupTokens.revokeFor(user.getId());
        users.delete(user);
    }

    private void sendInvitation(User user) {
        String token = setupTokens.issueFor(user.getId());
        String link = frontendUrl + "/crear-contrasena?token=" + token;
        mail.sendInvitation(user.getEmail(), user.getName(), link, inviteProperties.expiration());
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
    }
}
