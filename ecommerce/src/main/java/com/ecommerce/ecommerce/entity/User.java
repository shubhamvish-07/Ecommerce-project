package com.ecommerce.ecommerce.entity;

import com.ecommerce.ecommerce.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(length = 100)
    private String recipientName;

    @Column(length = 10)
    private String recipientPhone;

    @Column(length = 200)
    private String deliveryAddress;

    @Column(length = 80)
    private String deliveryCity;

    @Column(length = 80)
    private String deliveryState;

    @Column(length = 6)
    private String deliveryPostalCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}