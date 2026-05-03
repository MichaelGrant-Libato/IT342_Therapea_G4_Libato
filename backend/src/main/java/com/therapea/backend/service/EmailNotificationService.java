package com.therapea.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendApplicationReceived(String email, String doctorName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("TheraPea: Application Received");
        message.setText("Dear Dr. " + doctorName + ",\n\n" +
                "This email is to formally confirm that we have received your application to join the TheraPea platform. " +
                "Your submitted documents and clinical biography are currently under review by our administration team.\n\n" +
                "We will notify you via email as soon as a decision has been made.\n\n" +
                "Thank you for your interest in providing care with TheraPea.\n\n" +
                "Sincerely,\n" +
                "The TheraPea Administration Team");
        mailSender.send(message);
    }

    public void sendApplicationApproved(String email, String doctorName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("TheraPea: Application Approved");
        message.setText("Dear Dr. " + doctorName + ",\n\n" +
                "Congratulations! We are pleased to formally inform you that your application has been approved. " +
                "Your credentials have been verified, and your provider account is now fully active.\n\n" +
                "You may now log in to your TheraPea Dashboard to set up your schedule and begin accepting patients.\n\n" +
                "Welcome to the team!\n\n" +
                "Sincerely,\n" +
                "The TheraPea Administration Team");
        mailSender.send(message);
    }

    public void sendApplicationRejected(String email, String doctorName, String reason) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("TheraPea: Application Status Update");
        message.setText("Dear Dr. " + doctorName + ",\n\n" +
                "Thank you for applying to join the TheraPea platform. " +
                "After careful review of your application and provided documents, we regret to inform you that we are unable to approve your account at this time.\n\n" +
                "Reason for decision: " + reason + "\n\n" +
                "If you believe this is an error or if you need to provide updated documentation, please contact our support team.\n\n" +
                "Sincerely,\n" +
                "The TheraPea Administration Team");
        mailSender.send(message);
    }
}