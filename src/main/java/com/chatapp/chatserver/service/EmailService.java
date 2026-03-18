package com.chatapp.chatserver.service;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class EmailService {

    @Value("${sendgrid.api-key}")
    private String apiKey;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    private void sendEmail(String toEmail, String subject, String htmlContent) {
        Email from = new Email(fromEmail, "ChatApp");
        Email to = new Email(toEmail);
        Content content = new Content("text/html", htmlContent);
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid(apiKey);
        Request request = new Request();

        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            System.out.println("Email sent. Status: " + response.getStatusCode());
        } catch (IOException e) {
            System.err.println("Email send failed: " + e.getMessage());
        }
    }

    public void sendVerificationEmail(String toEmail, String token) {
        String verificationUrl = baseUrl + "/api/auth/verify?token=" + token;

        String html = """
            <div style="font-family: Arial, sans-serif; max-width: 480px; margin: auto; padding: 32px; border-radius: 12px; border: 1px solid #e5e5e5;">
                <h2 style="color: #5B4B8A;">Verify your ChatApp account</h2>
                <p style="color: #555;">Thanks for signing up! Click the button below to verify your email address.</p>
                <a href="%s" style="display:inline-block; margin-top:16px; padding: 12px 24px; background: #5B4B8A; color: white; border-radius: 8px; text-decoration: none; font-weight: bold;">
                    Verify Email
                </a>
                <p style="color: #aaa; font-size: 12px; margin-top: 24px;">This link expires in 24 hours. If you didn't sign up, ignore this email.</p>
            </div>
        """.formatted(verificationUrl);

        sendEmail(toEmail, "Verify your ChatApp account", html);
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetUrl = baseUrl + "/reset-password.html?token=" + token;

        String html = """
            <div style="font-family: Arial, sans-serif; max-width: 480px; margin: auto; padding: 32px; border-radius: 12px; border: 1px solid #e5e5e5;">
                <h2 style="color: #5B4B8A;">Reset your ChatApp password</h2>
                <p style="color: #555;">We received a request to reset your password. Click the button below to set a new one.</p>
                <a href="%s" style="display:inline-block; margin-top:16px; padding: 12px 24px; background: #5B4B8A; color: white; border-radius: 8px; text-decoration: none; font-weight: bold;">
                    Reset Password
                </a>
                <p style="color: #aaa; font-size: 12px; margin-top: 24px;">This link expires in 30 minutes. If you didn't request this, ignore this email.</p>
            </div>
        """.formatted(resetUrl);

        sendEmail(toEmail, "Reset your ChatApp password", html);
    }
}