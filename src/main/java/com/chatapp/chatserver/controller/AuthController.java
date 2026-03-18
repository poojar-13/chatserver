package com.chatapp.chatserver.controller;

import com.chatapp.chatserver.model.User;
import com.chatapp.chatserver.repository.UserRepository;
import com.chatapp.chatserver.security.JwtUtil;
import com.chatapp.chatserver.model.RefreshToken;
import com.chatapp.chatserver.repository.RefreshTokenRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.chatapp.chatserver.model.VerificationToken;
import com.chatapp.chatserver.repository.VerificationTokenRepository;
import com.chatapp.chatserver.service.EmailService;
import com.chatapp.chatserver.model.PasswordResetToken;
import com.chatapp.chatserver.repository.PasswordResetTokenRepository;


import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    

    public AuthController(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            VerificationTokenRepository verificationTokenRepository,
            EmailService emailService,
            PasswordResetTokenRepository passwordResetTokenRepository,
            RefreshTokenRepository refreshTokenRepository) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.verificationTokenRepository = verificationTokenRepository;
        this.emailService = emailService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
}

   

    // SIGN UP
    @PostMapping("/signup")
    public String signup(@RequestBody Map<String, String> body) {
    	String email = body.get("email");
    	String username = body.get("username");
    	String displayName = body.get("displayName");
    	String password = body.get("password");
    	
    	if (email == null || username == null ||
    		    displayName == null || password == null) {
    		    return "All fields are required";
    		}



    	if (userRepository.findByUsername(username).isPresent()) {
    	    return "Username already exists";
    	}

    	if (userRepository.findByEmail(email).isPresent()) {
    	    return "Email already registered";
    	}


    	User user = new User(
    	        email,
    	        username,
    	        displayName,
    	        passwordEncoder.encode(password)
    	);


    	userRepository.save(user);

    	// Create verification token
    	VerificationToken verificationToken = new VerificationToken(user);
    	verificationTokenRepository.save(verificationToken);

    	// Print token in console (temporary for testing)
    	emailService.sendVerificationEmail(
    	        user.getEmail(),
    	        verificationToken.getToken()
    	);


    	return "User registered successfully";

    }

    // LOGIN
    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Map<String, String> body) {

        String email = body.get("email");
        String password = body.get("password");

        return userRepository.findByEmail(email)
                .filter(user -> passwordEncoder.matches(password, user.getPassword()))
                .map(user -> {

                    if (!user.isEmailVerified()) {
                        return Map.of("error", "Please verify your email first");
                    }

                    String token = jwtUtil.generateToken(user.getUsername());

                    refreshTokenRepository.deleteByUser_Id(user.getId());
                    RefreshToken refreshToken = new RefreshToken(user);
                    refreshTokenRepository.save(refreshToken);

                    return Map.of(
                            "token", token,
                            "refreshToken", refreshToken.getToken(),
                            "username", user.getUsername(),
                            "displayName", user.getDisplayName()
                    );
                })
                .orElse(Map.of("error", "Invalid credentials"));
    }
    
    @GetMapping("/verify")
    public String verifyEmail(@RequestParam("token") String token) {

        return verificationTokenRepository.findByToken(token)
                .map(verificationToken -> {

                    if (verificationToken.getExpiryDate()
                            .isBefore(java.time.LocalDateTime.now())) {
                        return "Token expired";
                    }

                    User user = verificationToken.getUser();
                    user.setEmailVerified(true);
                    userRepository.save(user);

                    return "Email verified successfully";
                })
                .orElse("Invalid token");
    }
    
    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestBody Map<String, String> body) {

        String email = body.get("email");

        if (email == null) {
            return "Email is required";
        }

        return userRepository.findByEmail(email)
                .map(user -> {

                    // Remove existing reset token if present
                    passwordResetTokenRepository.findByUser_Id(user.getId())
                            .ifPresent(passwordResetTokenRepository::delete);

                    // Create new reset token
                    PasswordResetToken resetToken = new PasswordResetToken(user);
                    passwordResetTokenRepository.save(resetToken);

                    // Send reset email
                    String resetUrl =
                            "http://localhost:8080/reset-password.html?token="
                                    + resetToken.getToken();

                    emailService.sendPasswordResetEmail(
                            user.getEmail(),
                            resetToken.getToken()
                    );

                    return "Password reset email sent";
                })
                .orElse("If that email exists, a reset link has been sent");
    }
    
    @PostMapping("/reset-password")
    public String resetPassword(@RequestBody Map<String, String> body) {

        String token = body.get("token");
        String newPassword = body.get("newPassword");

        if (token == null || newPassword == null) {
            return "Token and new password are required";
        }

        return passwordResetTokenRepository.findByToken(token)
                .map(resetToken -> {

                    if (resetToken.getExpiryDate()
                            .isBefore(java.time.LocalDateTime.now())) {
                        return "Reset token expired";
                    }

                    User user = resetToken.getUser();
                    user.setPassword(passwordEncoder.encode(newPassword));
                    userRepository.save(user);

                    // Delete token after successful reset
                    passwordResetTokenRepository.delete(resetToken);

                    return "Password reset successfully";
                })
                .orElse("Invalid reset token");
    }
    
    @PostMapping("/refresh")
    public Map<String, String> refresh(@RequestBody Map<String, String> body) {

        String refreshToken = body.get("refreshToken");

        if (refreshToken == null) {
            return Map.of("error", "Refresh token required");
        }

        return refreshTokenRepository.findByToken(refreshToken)
                .map(rt -> {

                    if (rt.getExpiryDate().isBefore(java.time.LocalDateTime.now())) {
                        refreshTokenRepository.delete(rt);
                        return Map.of("error", "Refresh token expired");
                    }

                    String newToken = jwtUtil.generateToken(rt.getUser().getUsername());
                    return Map.of("token", newToken);
                })
                .orElse(Map.of("error", "Invalid refresh token"));
    }

    @PostMapping("/logout")
    public String logout(@RequestBody Map<String, String> body) {

        String refreshToken = body.get("refreshToken");

        if (refreshToken != null) {
            refreshTokenRepository.findByToken(refreshToken)
                    .ifPresent(refreshTokenRepository::delete);
        }

        return "Logged out successfully";
    }


}
