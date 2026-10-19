package org.scoutsdecanarias.tha_backend.features.mfa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMfaRepository extends JpaRepository<UserMfa, Long> {
}
