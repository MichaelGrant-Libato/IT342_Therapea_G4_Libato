package com.therapea.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.therapea.app.R
import com.therapea.app.network.*
import com.therapea.app.ui.onboarding.DoctorOnboardingFragment
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class RegisterActivity : FragmentActivity() {
    lateinit var viewModel: RegisterViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[RegisterViewModel::class.java]
        setContentView(R.layout.activity_register_host)

        val isGoogle = intent.getBooleanExtra("isGoogle", false)
        if (isGoogle) {
            viewModel.email.value = intent.getStringExtra("email")
            viewModel.fullName.value = intent.getStringExtra("name")
            viewModel.isGoogleUser.value = true
            viewModel.password.value = "OAUTH_USER_${java.util.UUID.randomUUID()}"
            showStep2()
        } else {
            showStep1()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack()
                else finish()
            }
        })
    }

    fun showStep1() {
        supportFragmentManager.beginTransaction().replace(R.id.register_container, RegisterStep1Fragment()).commit()
    }

    fun showStep2() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.register_container, RegisterStep2Fragment()).addToBackStack(null).commit()
    }

    fun showDoctorOnboarding() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.register_container, DoctorOnboardingFragment()).addToBackStack(null).commit()
    }

    fun showStep3() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.register_container, RegisterStep3Fragment()).addToBackStack(null).commit()
    }

    fun performFinalSubmit() {
        viewModel.isLoading.value = true
        lifecycleScope.launch {
            try {
                val regReq = RegisterRequest(
                    viewModel.fullName.value!!,
                    viewModel.email.value!!,
                    viewModel.password.value!!,
                    viewModel.role.value!!
                )

                val regRes = withContext(Dispatchers.IO) { ApiClient.authService.register(regReq) }

                if (regRes.isSuccessful) {
                    if (viewModel.role.value == "DOCTOR") {
                        handleDoctorVerificationUpload()
                    }

                    Toast.makeText(this@RegisterActivity, "Registration Successful!", Toast.LENGTH_LONG).show()

                    val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    val error = regRes.errorBody()?.string() ?: "Registration Failed"
                    Toast.makeText(this@RegisterActivity, error, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RegisterActivity, "Connection Error", Toast.LENGTH_SHORT).show()
            } finally {
                viewModel.isLoading.value = false
            }
        }
    }

    private suspend fun handleDoctorVerificationUpload() {
        try {
            val emailPart = viewModel.email.value!!.toRequestBody("text/plain".toMediaTypeOrNull())
            val bioPart = viewModel.clinicalBio.value!!.toRequestBody("text/plain".toMediaTypeOrNull())
            val ratePart = viewModel.hourlyRate.value!!.toRequestBody("text/plain".toMediaTypeOrNull())

            val file = uriToFile(viewModel.prcFileUri.value!!)
            val requestFile = file.asRequestBody("application/pdf".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("prcLicense", file.name, requestFile)

            withContext(Dispatchers.IO) {
                ApiClient.authService.doctorVerification(emailPart, bioPart, ratePart, filePart)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun uriToFile(uri: android.net.Uri): File {
        val inputStream = contentResolver.openInputStream(uri)
        val file = File(cacheDir, "temp_prc_upload.pdf")
        val outputStream = FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        outputStream.close()
        inputStream?.close()
        return file
    }
}