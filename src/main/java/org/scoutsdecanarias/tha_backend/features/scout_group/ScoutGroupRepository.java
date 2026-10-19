package org.scoutsdecanarias.tha_backend.features.scout_group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ScoutGroupRepository extends JpaRepository<ScoutGroup, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<ScoutGroup> findAllByOrderByNameAsc();

    List<ScoutGroup> findByActiveTrueOrderByNameAsc();

    /** Pares {identificador del grupo, número de personas} de los grupos que tienen alguna. */
    @Query("select u.scoutGroup.id, count(u) from User u where u.scoutGroup is not null group by u.scoutGroup.id")
    List<Object[]> countMembersByGroup();
}
