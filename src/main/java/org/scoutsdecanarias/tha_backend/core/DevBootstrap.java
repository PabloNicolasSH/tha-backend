package org.scoutsdecanarias.tha_backend.core;

import org.scoutsdecanarias.tha_backend.features.scout_group.ScoutGroup;
import org.scoutsdecanarias.tha_backend.features.scout_group.ScoutGroupRepository;
import org.scoutsdecanarias.tha_backend.features.user.Role;
import org.scoutsdecanarias.tha_backend.features.user.User;
import org.scoutsdecanarias.tha_backend.features.user.UserRepository;
import org.scoutsdecanarias.tha_backend.features.user.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * Datos de arranque en local: una cuenta administradora si no hay usuarios y el grupo Bentaya (el del piloto) si no
 * hay grupos. Nunca se activa en producción.
 */
@Component
@Profile("dev")
class DevBootstrap implements ApplicationRunner {

    private final UserRepository users;
    private final ScoutGroupRepository groups;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    DevBootstrap(UserRepository users, ScoutGroupRepository groups, PasswordEncoder passwordEncoder,
                 @Value("${tha.bootstrap-admin.email}") String email,
                 @Value("${tha.bootstrap-admin.password}") String password) {
        this.users = users;
        this.groups = groups;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (groups.count() == 0) {
            ScoutGroup bentaya = new ScoutGroup();
            bentaya.setName("Bentaya");
            groups.save(bentaya);
        }
        if (users.count() > 0) {
            return;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setName("Admin");
        admin.setSurname("Tha");
        admin.setStatus(UserStatus.ACTIVE);
        admin.setRoles(EnumSet.of(Role.ADMIN, Role.MEMBER));
        users.save(admin);
    }
}
