package com.spotilike.userservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Getter
@Setter
@Table(name = "tokens", indexes = {
        @Index(name = "idx_tokens_user_id", columnList = "user_id"),
        @Index(name = "idx_tokens_expires_at", columnList = "expires_at"),
        @Index(name = "idx_tokens_user_active",
                columnList = "user_id, revoked_at, expires_at")
})
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"user"})
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tokens_id_seq")
    @SequenceGenerator(
            name = "tokens_id_seq",
            sequenceName = "tokens_id_seq",
            allocationSize = 50
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "ip_address")
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private String ipAddress;

    @Column(name = "device_info", nullable = false)
    private String deviceInfo;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private OffsetDateTime createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RefreshToken other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
