package org.scoutsdecanarias.tha_backend.features.sso;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, Long> {

    @Query("select e from ExternalIdentity e join fetch e.user where e.provider = :provider and e.subject = :subject")
    Optional<ExternalIdentity> findByProviderAndSubject(@Param("provider") String provider, @Param("subject") String subject);
}
