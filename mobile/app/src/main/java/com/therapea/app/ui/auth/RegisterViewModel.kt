package com.therapea.app.ui.auth

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class RegisterViewModel : ViewModel() {
    val fullName = MutableLiveData("")
    val email = MutableLiveData("")
    val password = MutableLiveData("")
    val role = MutableLiveData("PATIENT")

    // Google Specific
    val isGoogleUser = MutableLiveData(false)
    val googleIdToken = MutableLiveData("")

    // Doctor Specific
    val clinicalBio = MutableLiveData("")
    val hourlyRate = MutableLiveData("")
    val prcFileUri = MutableLiveData<Uri?>(null)

    val isLoading = MutableLiveData(false)

    fun isStep1Valid(): Boolean {
        return !fullName.value.isNullOrBlank() &&
                !email.value.isNullOrBlank() &&
                !password.value.isNullOrBlank()
    }
}