package com.chatapp.chatserver.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendVerificationEmail(String toEmail, String token) {

        String verificationUrl =
                "http://localhost:8080/api/auth/verify?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("yourgmail@gmail.com");
        message.setTo(toEmail);
        message.setSubject("Verify Your ChatApp Account");

        message.setText(
                "Hi,\n\n" +
                "Thank you for signing up for ChatApp.\n\n" +
                "Please verify your email by clicking the link below:\n\n" +
                verificationUrl +
                "\n\nThis link will expire in 24 hours.\n\n" +
                "If you did not sign up, you can ignore this email.\n\n" +
                "— ChatApp Team"
        );

        mailSender.send(message);
    }
    
    public void sendPasswordResetEmail(String toEmail, String token) {

        String resetUrl =
                "http://localhost:8080/reset-password.html?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("yourgmail@gmail.com");
        message.setTo(toEmail);
        message.setSubject("Reset Your ChatApp Password");

        message.setText(
                "Hi,\n\n" +
                "We received a request to reset your password.\n\n" +
                "Click the link below to set a new password:\n\n" +
                resetUrl +
                "\n\nThis link will expire in 30 minutes.\n\n" +
                "If you did not request this, you can ignore this email.\n\n" +
                "— ChatApp Team"
        );

        mailSender.send(message);
    }


}
