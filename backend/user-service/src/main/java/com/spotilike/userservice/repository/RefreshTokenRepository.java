package com.spotilike.userservice.repository;

import com.spotilike.userservice.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUser_Id(Long userId);

    int revokeByUser_IdAndDeviceInfo(Long userId, String deviceInfo);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true "
            + "WHERE t.user.id = :userId AND t.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);
}
