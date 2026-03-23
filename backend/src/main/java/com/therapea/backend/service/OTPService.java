package com.therapea.backend.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OTPService {

    private final Map<String, OTPData> otpStore = new ConcurrentHashMap<>();

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
        long expiryTime = System.currentTimeMillis() + (10 * 60 * 1000);
        otpStore.put(email, new OTPData(otp, expiryTime));
    }

    public boolean verifyOTP(String email, String otp) {
        OTPData otpData = otpStore.get(email);

        if (otpData == null) return false;

        if (System.currentTimeMillis() > otpData.expiryTime) {
            otpStore.remove(email);
            return false;
        }

        return otp.equals(otpData.otp);
    }

    public void clearOTP(String email) {
        otpStore.remove(email);
    }
}