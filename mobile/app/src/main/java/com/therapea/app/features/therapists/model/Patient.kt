package com.therapea.app.features.therapists.model

data class Patient(
    val id: String,
    val name: String,
    val email: String,
    val status: String,
    val risk: String
)