package com.totemena.elite.auth;

import com.totemena.elite.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @Column(name = "device_name", length = 150)
    private String deviceName;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "logged_in_at", nullable = false)
    @Builder.Default
    private Instant loggedInAt = Instant.now();
}
