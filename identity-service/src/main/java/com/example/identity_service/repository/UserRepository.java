package com.example.identity_service.repository;

import com.example.identity_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    // Tìm user, trả về Optional để tránh lỗi NullPointerException
    Optional<User> findByUsername(String username);

    //Giúp trả về những trường tối thiểu, tiết kiệm băng thông
    @Query("SELECT new com.example.identity_service.entity.User(u.username, u.role) FROM User u WHERE u.username = :username")
    Optional<User> findShadowUser(@Param("username") String username);

    boolean existsByUsername(String username);
}
