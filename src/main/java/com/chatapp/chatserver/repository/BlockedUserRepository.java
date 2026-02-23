package com.chatapp.chatserver.repository;

import com.chatapp.chatserver.model.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import java.util.Optional;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {

    Optional<BlockedUser> findByBlockerAndBlocked(String blocker, String blocked);

    boolean existsByBlockerAndBlocked(String blocker, String blocked);

    List<BlockedUser> findByBlocker(String blocker);

    List<BlockedUser> findByBlocked(String blocked);

}

