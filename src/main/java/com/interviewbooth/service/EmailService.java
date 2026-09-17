package com.interviewbooth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        // The reset link points at the deployed frontend with the token in the query
        // string; the frontend reads it on page load and shows the "set new password" form.
        String resetLink = frontendUrl + "?reset=" + resetToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Reset your AI Interview Booth password");
        message.setText(
                "Hi,\n\n" +
                "We received a request to reset your AI Interview Booth password.\n\n" +
                "Click the link below to set a new password (valid for 1 hour):\n" +
                resetLink + "\n\n" +
                "If you didn't request this, you can safely ignore this email.\n\n" +
                "- AI Interview Booth"
        );
        mailSender.send(message);
    }
}
