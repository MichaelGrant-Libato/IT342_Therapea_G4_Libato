package com.therapea.app.core.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface AuthApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<DashboardDTO>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<DashboardDTO>

    @POST("api/auth/google-check")
    suspend fun googleCheck(@Body body: Map<String, String>): Response<DashboardDTO>

    @POST("api/auth/send-otp")
    suspend fun sendOtp(@Body body: Map<String, String>): Response<OtpResponse>

    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body body: Map<String, String>): Response<OtpResponse>

    @POST("api/auth/google-login")
    suspend fun googleLogin(@Body body: Map<String, String>): Response<DashboardDTO>

    @Multipart
    @POST("api/auth/doctor-verification")
    suspend fun doctorVerification(
        @Part("email") email: RequestBody,
        @Part("clinicalBio") bio: RequestBody,
        @Part("hourlyRate") rate: RequestBody,
        @Part prcLicense: MultipartBody.Part
    ): Response<Map<String, Any>>
}