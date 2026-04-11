package com.therapea.app.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.therapea.app.R

class RegisterStep2Fragment : Fragment(R.layout.fragment_register_step2) {
    private val viewModel: RegisterViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val cardPatient = view.findViewById<LinearLayout>(R.id.cardPatient)
        val cardDoctor = view.findViewById<LinearLayout>(R.id.cardDoctor)
        val btnNext = view.findViewById<Button>(R.id.btnStep2Next)

        cardPatient.setOnClickListener {
            viewModel.role.value = "PATIENT"
            cardPatient.setBackgroundResource(R.drawable.bg_input_selected)
            cardDoctor.setBackgroundResource(R.drawable.bg_input)
        }

        cardDoctor.setOnClickListener {
            viewModel.role.value = "DOCTOR"
            cardDoctor.setBackgroundResource(R.drawable.bg_input_selected)
            cardPatient.setBackgroundResource(R.drawable.bg_input)
        }

        btnNext.setOnClickListener {
            if (viewModel.role.value == "DOCTOR") {
                (activity as? RegisterActivity)?.showDoctorOnboarding()
            } else {
                (activity as? RegisterActivity)?.showStep3()
            }
        }
    }
}