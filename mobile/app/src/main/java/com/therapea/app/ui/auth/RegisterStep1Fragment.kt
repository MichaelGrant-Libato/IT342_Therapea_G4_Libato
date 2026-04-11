package com.therapea.app.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.therapea.app.R
import com.therapea.app.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterStep1Fragment : Fragment(R.layout.fragment_register_step1) {
    private val viewModel: RegisterViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val etName = view.findViewById<EditText>(R.id.etRegName)
        val etEmail = view.findViewById<EditText>(R.id.etRegEmail)
        val etPass = view.findViewById<EditText>(R.id.etRegPassword)
        val etConfirm = view.findViewById<EditText>(R.id.etRegConfirmPassword)
        val btnContinue = view.findViewById<Button>(R.id.btnContinue)
        val btnGoogle = view.findViewById<LinearLayout>(R.id.btnRegisterGoogle)

        btnContinue.setOnClickListener {
            if (validateInputs(etName, etEmail, etPass, etConfirm)) {
                viewModel.fullName.value = etName.text.toString().trim()
                viewModel.email.value = etEmail.text.toString().trim()
                viewModel.password.value = etPass.text.toString()
                viewModel.isGoogleUser.value = false
                (activity as? RegisterActivity)?.showStep2()
            }
        }

        btnGoogle.setOnClickListener {
            handleGoogleRegister()
        }
    }

    private fun validateInputs(
        etName: EditText,
        etEmail: EditText,
        etPass: EditText,
        etConfirm: EditText
    ): Boolean {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val pass = etPass.text.toString()
        val confirm = etConfirm.text.toString()

        var isValid = true

        if (name.isEmpty()) {
            etName.error = "Full name is required"
            isValid = false
        }

        if (email.isEmpty()) {
            etEmail.error = "Email is required"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Please enter a valid email address"
            isValid = false
        }

        if (pass.isEmpty()) {
            etPass.error = "Password is required"
            isValid = false
        } else if (pass.length < 8) {
            etPass.error = "Password must be at least 8 characters"
            isValid = false
        }

        if (confirm != pass) {
            etConfirm.error = "Passwords do not match"
            isValid = false
        }

        if (!isValid) {
            Toast.makeText(context, "Please correct the errors above", Toast.LENGTH_SHORT).show()
        }

        return isValid
    }

    private fun handleGoogleRegister() {
        val cm = androidx.credentials.CredentialManager.create(requireContext())
        val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(com.therapea.app.BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = androidx.credentials.GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = cm.getCredential(requireContext(), request)
                val cred = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(result.credential.data)

                val response = withContext(Dispatchers.IO) {
                    ApiClient.authService.googleCheck(mapOf("idToken" to cred.idToken))
                }

                if (response.isSuccessful) {
                    Toast.makeText(context, "Account already exists. Please Login.", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.fullName.value = cred.displayName ?: ""
                    viewModel.email.value = cred.id
                    viewModel.password.value = "OAUTH_USER_${java.util.UUID.randomUUID()}"
                    viewModel.isGoogleUser.value = true
                    (activity as? RegisterActivity)?.showStep2()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Google registration cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }
}