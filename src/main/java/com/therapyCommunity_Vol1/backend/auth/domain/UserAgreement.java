package com.therapyCommunity_Vol1.backend.auth.domain;

import com.therapyCommunity_Vol1.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "user_agreements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_type", nullable = false, length = 50)
    private AgreementType agreementType;

    @Column(nullable = false, length = 10)
    private String version;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    public static UserAgreement create(User user, AgreementType type, String version) {
        UserAgreement agreement = new UserAgreement();
        agreement.user = user;
        agreement.agreementType = type;
        agreement.version = version;
        agreement.agreedAt = LocalDateTime.now();
        return agreement;
    }
}
