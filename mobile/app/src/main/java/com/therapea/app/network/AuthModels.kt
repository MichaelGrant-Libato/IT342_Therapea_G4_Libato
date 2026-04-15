package com.therapea.app.network


data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val fullName: String,
    val email: String,
    val password: String,
    val role: String
)

data class DashboardDTO(
    val userId: String? = null,
    val fullName: String? = null,
    val email: String? = null,
    val role: String? = null,
    val message: String? = null
)

// For your OTP responses
data class OtpResponse(
    val success: Boolean,
    val message: String,
    val otp: String? = null
)