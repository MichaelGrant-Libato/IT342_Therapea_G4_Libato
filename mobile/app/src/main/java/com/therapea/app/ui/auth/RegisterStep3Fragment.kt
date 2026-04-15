package com.therapea.app.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.therapea.app.R

class RegisterStep3Fragment : Fragment(R.layout.fragment_register_step3) {
    private val viewModel: RegisterViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvName = view.findViewById<TextView>(R.id.tvReviewName)
        val tvEmail = view.findViewById<TextView>(R.id.tvReviewEmail)
        val tvRole = view.findViewById<TextView>(R.id.tvReviewRole)
        val btnSubmit = view.findViewById<Button>(R.id.btnFinalSubmit)
        val cbTerms = view.findViewById<CheckBox>(R.id.cbTermsFinal)
        val layoutDoctor = view.findViewById<LinearLayout>(R.id.layoutDoctorReview)
        val tvBio = view.findViewById<TextView>(R.id.tvReviewBio)
        val tvRate = view.findViewById<TextView>(R.id.tvReviewRate)

        tvName.text = "Full Name: ${viewModel.fullName.value}"
        tvEmail.text = "Email: ${viewModel.email.value}"
        val userRole = viewModel.role.value ?: "PATIENT"
        tvRole.text = "Account Type: $userRole"

        if (userRole == "DOCTOR") {
            layoutDoctor.visibility = View.VISIBLE
            tvBio.text = viewModel.clinicalBio.value
            tvRate.text = "₱${viewModel.hourlyRate.value}.00 / hour"
        } else {
            layoutDoctor.visibility = View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            btnSubmit.isEnabled = !loading
            btnSubmit.text = if (loading) "Processing..." else "Complete Registration"
        }

        btnSubmit.setOnClickListener {
            if (cbTerms.isChecked) {
                (activity as? RegisterActivity)?.performFinalSubmit()
            } else {
                Toast.makeText(context, "Please agree to the terms", Toast.LENGTH_SHORT).show()
            }
        }
    }
}