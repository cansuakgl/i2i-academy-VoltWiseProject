package com.wattsmart.backend.auth.repository;

import com.wattsmart.backend.auth.domain.UserRole;
import com.wattsmart.backend.auth.domain.UserRoleAssignment;
import com.wattsmart.backend.auth.domain.UserRoleAssignmentId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UserRoleAssignmentId> {

    @Query("""
            select assignment.role
            from UserRoleAssignment assignment
            where assignment.user.id = :userId
            order by assignment.role
            """)
    List<UserRole> findRolesByUserId(UUID userId);

    List<UserRoleAssignment> findByUserId(UUID userId);

    long countByRole(UserRole role);

    @Modifying
    @Query("delete from UserRoleAssignment assignment where assignment.user.id = :userId")
    void deleteByUserId(UUID userId);
}
