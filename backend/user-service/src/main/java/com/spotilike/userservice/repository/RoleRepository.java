package com.spotilike.userservice.repository;

import com.spotilike.userservice.model.Role;
import com.spotilike.userservice.model.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleName name);
}
