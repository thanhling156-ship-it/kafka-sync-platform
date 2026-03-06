package com.example.identity_service.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String password; // Phải được mã hóa BCrypt nhé!
    private String email;

    @Enumerated(EnumType.STRING) // Lưu dưới dạng chuỗi (BASIC, VIP...) vào DB cho dễ đọc
    private UserRole role;

    @Version
    private Long version;

    public User() {
    }

    public User(Long id, String password, String username, UserRole role) {
        this.id = id;
        this.password = password;
        this.username = username;
        this.role = role;
    }

    public User(String username, UserRole role) {
        this.username = username;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}