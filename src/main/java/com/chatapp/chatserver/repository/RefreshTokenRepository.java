package com.chatapp.chatserver.repository;

import com.chatapp.chatserver.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    
    @Transactional  // ← ADD THIS
    void deleteByUser_Id(Long userId);
}