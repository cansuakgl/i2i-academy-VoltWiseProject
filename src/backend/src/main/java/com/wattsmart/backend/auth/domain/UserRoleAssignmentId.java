package com.wattsmart.backend.auth.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class UserRoleAssignmentId implements Serializable {

    private UUID user;
    private UserRole role;

    public UserRoleAssignmentId() {
    }

    public UserRoleAssignmentId(UUID user, UserRole role) {
        this.user = user;
        this.role = role;
    }

    public UUID getUser() {
        return user;
    }

    public void setUser(UUID user) {
        this.user = user;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserRoleAssignmentId that)) {
            return false;
        }
        return Objects.equals(user, that.user) && role == that.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, role);
    }
}
