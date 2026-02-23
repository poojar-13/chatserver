package com.chatapp.chatserver.model;

import jakarta.persistence.*;

@Entity
@Table(name = "blocked_users")
public class BlockedUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String blocker;
    private String blocked;

    public BlockedUser() {}

    public BlockedUser(String blocker, String blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
    }

    public Long getId() {
        return id;
    }

    public String getBlocker() {
        return blocker;
    }

    public String getBlocked() {
        return blocked;
    }
}
