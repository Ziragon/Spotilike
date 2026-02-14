package com.spotilike.userservice.repository;

import com.spotilike.userservice.dto.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
}
