package com.therapea.backend.features.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OTPService {

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Autowired
    private JavaMailSender mailSender;

    private final Map<String, OTPData> otpStore = new ConcurrentHashMap<>();
    private final Map<String, Boolean> verifiedEmails = new ConcurrentHashMap<>();

    private static class OTPData {
        String otp;
        long expiryTime;

        OTPData(String otp, long expiryTime) {
            this.otp = otp;
            this.expiryTime = expiryTime;
        }
    }

    public String generateOTP() {
        return String.format("%06d", (int)(Math.random() * 900000) + 100000);
    }

    public void storeOTP(String email, String otp) {
        long expiryTime = System.currentTimeMillis() + (10 * 60 * 1000); // 10 minutes expiry
        otpStore.put(email, new OTPData(otp, expiryTime));
    }

    public void generateAndSendOtp(String email, String type) {
        String otp = generateOTP();
        storeOTP(email, otp);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setFrom(fromEmail);

        if ("FORGOT_PASSWORD".equalsIgnoreCase(type)) {
            message.setSubject("Reset Your TheraPea Password");
            message.setText("Hello,\n\n" +
                    "We received a request to reset the password for your TheraPea account. " +
                    "Your verification code is:\n\n" +
                    otp + "\n\n" +
                    "If you did not request this, please ignore this email.");
        } else if ("LOGIN".equalsIgnoreCase(type)) {
            message.setSubject("Sign in to TheraPea");
            message.setText("Welcome back to TheraPea!\n\n" +
                    "To securely sign in, please enter the following 6-digit code:\n\n" +
                    otp);
        } else {
            message.setSubject("Verify your TheraPea Account");
            message.setText("Welcome to TheraPea!\n\n" +
                    "To verify your email address, please enter the following 6-digit code:\n\n" +
                    otp);
        }

        mailSender.send(message);
    }

    public boolean verifyOtp(String email, String otp) {
        OTPData otpData = otpStore.get(email);
        if (otpData == null) return false;

        if (System.currentTimeMillis() > otpData.expiryTime) {
            otpStore.remove(email);
            return false;
        }

        if (otp.equals(otpData.otp)) {
            otpStore.remove(email);
            verifiedEmails.put(email, true);
            return true;
        }
        return false;
    }

    public void clearOTP(String email) {
        otpStore.remove(email);
    }

    public boolean isEmailVerified(String email) {
        return verifiedEmails.getOrDefault(email, false);
    }

    public void clearVerification(String email) {
        verifiedEmails.remove(email);
    }
}