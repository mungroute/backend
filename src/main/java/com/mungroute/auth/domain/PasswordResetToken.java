package com.mungroute.auth.domain;

import com.mungroute.user.domain.AppUser;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "password_reset_token_id")
    private Long passwordResetTokenId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "verification_code_hash", nullable = false, length = 64)
    private String verificationCodeHash;

    @Column(name = "reset_token_hash", unique = true, length = 64)
    private String resetTokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected PasswordResetToken() {
    }

    private PasswordResetToken(AppUser user, String verificationCodeHash, OffsetDateTime expiresAt) {
        this.user = user;
        this.verificationCodeHash = verificationCodeHash;
        this.expiresAt = expiresAt;
        this.createdAt = OffsetDateTime.now();
    }

    public static PasswordResetToken issue(AppUser user, String codeHash, OffsetDateTime expiresAt) {
        return new PasswordResetToken(user, codeHash, expiresAt);
    }

    public boolean canVerify(String codeHash, OffsetDateTime now) {
        return usedAt == null && verifiedAt == null && expiresAt.isAfter(now)
                && verificationCodeHash.equals(codeHash);
    }

    public void verify(String resetTokenHash, OffsetDateTime now, OffsetDateTime resetExpiresAt) {
        this.resetTokenHash = resetTokenHash;
        this.verifiedAt = now;
        this.expiresAt = resetExpiresAt;
    }

    public boolean canResetAt(OffsetDateTime now) {
        return usedAt == null && verifiedAt != null && expiresAt.isAfter(now);
    }

    public void markUsed(OffsetDateTime now) {
        this.usedAt = now;
    }

    public AppUser getUser() {
        return user;
    }
}
