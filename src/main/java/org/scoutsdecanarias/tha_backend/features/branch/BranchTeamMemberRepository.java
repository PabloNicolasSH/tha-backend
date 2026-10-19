package org.scoutsdecanarias.tha_backend.features.branch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface BranchTeamMemberRepository extends JpaRepository<BranchTeamMember, Long> {

    /** Puestos de la persona vigentes en la fecha indicada. */
    @Query("select m.position from BranchTeamMember m where m.user.id = :userId and m.startDate <= :today and m.endDate >= :today")
    List<TeamPosition> activePositions(Long userId, LocalDate today);

    /** ¿Tiene la persona algún puesto vigente en esa rama? */
    @Query("select count(m) > 0 from BranchTeamMember m where m.user.id = :userId and m.branch.id = :branchId and m.startDate <= :today and m.endDate >= :today")
    boolean hasActivePosition(Long userId, Long branchId, LocalDate today);
}
