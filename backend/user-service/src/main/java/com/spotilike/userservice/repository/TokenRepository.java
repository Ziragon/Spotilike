package com.spotilike.userservice.repository;

import com.spotilike.userservice.dto.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenRepository extends JpaRepository<RefreshToken, Long> {
}
