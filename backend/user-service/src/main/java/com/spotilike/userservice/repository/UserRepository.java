package com.spotilike.userservice.repository;

import com.spotilike.userservice.dto.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
