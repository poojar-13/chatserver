package com.chatapp.chatserver.controller;

import com.chatapp.chatserver.model.User;
import com.chatapp.chatserver.repository.UserRepository;
import org.springframework.web.bind.annotation.*;
import com.chatapp.chatserver.dto.UpdateUsernameRequest;
import com.chatapp.chatserver.dto.ChangePasswordRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.chatapp.chatserver.model.BlockedUser;
import com.chatapp.chatserver.repository.BlockedUserRepository;
import java.util.Map;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@CrossOrigin
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BlockedUserRepository blockedUserRepository;

   
    public UserController(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            BlockedUserRepository blockedUserRepository) {
this.userRepository = userRepository;
this.passwordEncoder = passwordEncoder;
this.blockedUserRepository = blockedUserRepository;
}


    @GetMapping
    public List<String> getAllUsers(Authentication authentication) {

        String currentUser = authentication.getName();

        // Users I blocked
        List<String> blockedByMe = blockedUserRepository
                .findByBlocker(currentUser)
                .stream()
                .map(BlockedUser::getBlocked)
                .toList();

        // Users who blocked me
        List<String> blockedMe = blockedUserRepository
                .findByBlocked(currentUser)
                .stream()
                .map(BlockedUser::getBlocker)
                .toList();

        return userRepository.findAll()
                .stream()
                .map(User::getUsername)
                .filter(username ->
                        !username.equals(currentUser)
                        && !blockedByMe.contains(username)
                        && !blockedMe.contains(username)
                )
                .toList();
    }

    
    @PutMapping("/update-username")
    public String updateUsername(
            @RequestBody UpdateUsernameRequest request,
            Authentication authentication
    ) {

        String currentUsername = authentication.getName();

        if (request.getNewUsername() == null ||
            request.getNewUsername().isBlank()) {
            return "Username cannot be empty";
        }

        if (userRepository.findByUsername(request.getNewUsername()).isPresent()) {
            return "Username already taken";
        }

        return userRepository.findByUsername(currentUsername)
                .map(user -> {
                    user.setUsername(request.getNewUsername());
                    userRepository.save(user);
                    return "Username updated successfully";
                })
                .orElse("User not found");
    }

    @PutMapping("/change-password")
    public String changePassword(
            @RequestBody ChangePasswordRequest request,
            Authentication authentication
    ) {

        String username = authentication.getName();

        return userRepository.findByUsername(username)
                .map(user -> {

                    if (!passwordEncoder.matches(
                            request.getOldPassword(),
                            user.getPassword()
                    )) {
                        return "Old password is incorrect";
                    }

                    user.setPassword(
                            passwordEncoder.encode(request.getNewPassword())
                    );

                    userRepository.save(user);
                    return "Password changed successfully";
                })
                .orElse("User not found");
    }
    
    @PostMapping("/block")
    public String blockUser(@RequestBody Map<String, String> body) {

        String blocker = body.get("blocker");
        String blocked = body.get("blocked");

        if (blocker == null || blocked == null) {
            return "Invalid request";
        }

        if (blockedUserRepository.existsByBlockerAndBlocked(blocker, blocked)) {
            return "Already blocked";
        }

        blockedUserRepository.save(new BlockedUser(blocker, blocked));

        return "User blocked successfully";
    }
    
    @DeleteMapping("/unblock")
    public String unblockUser(@RequestBody Map<String, String> body) {

        String blocker = body.get("blocker");
        String blocked = body.get("blocked");

        if (blocker == null || blocked == null) {
            return "Invalid request";
        }

        blockedUserRepository
            .findByBlockerAndBlocked(blocker, blocked)
            .ifPresent(blockedUserRepository::delete);

        return "User unblocked successfully";
    }

    
    @GetMapping("/is-blocked/{blocker}/{blocked}")
    public Map<String, Boolean> isBlocked(
            @PathVariable String blocker,
            @PathVariable String blocked
    ) {
        boolean blockedStatus =
                blockedUserRepository.existsByBlockerAndBlocked(blocker, blocked);

        return Map.of("blocked", blockedStatus);
    }
    
    @GetMapping("/blocked/{username}")
    public List<String> getBlockedUsers(@PathVariable String username) {
        return blockedUserRepository
                .findByBlocker(username)
                .stream()
                .map(b -> b.getBlocked())
                .toList();
    }





}

