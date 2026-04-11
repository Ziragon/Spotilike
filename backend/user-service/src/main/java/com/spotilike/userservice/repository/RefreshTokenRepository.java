package com.spotilike.userservice.repository;

import com.spotilike.userservice.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = CURRENT_TIMESTAMP " +
            "WHERE t.user.id = :userId AND t.deviceInfo = :deviceInfo AND t.revokedAt IS NULL")
    int revokeByUserIdAndDeviceInfo(@Param("userId") Long userId,
                                    @Param("deviceInfo") String deviceInfo);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = CURRENT_TIMESTAMP "
            + "WHERE t.user.id = :userId AND t.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId);
}
