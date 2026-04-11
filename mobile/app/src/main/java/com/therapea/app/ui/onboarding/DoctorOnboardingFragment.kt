package com.therapea.app.ui.onboarding

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.therapea.app.R
import com.therapea.app.ui.auth.RegisterActivity
import com.therapea.app.ui.auth.RegisterViewModel

class DoctorOnboardingFragment : Fragment(R.layout.fragment_doctor_onboarding) {
    private val viewModel: RegisterViewModel by activityViewModels()
    private lateinit var tvFileStatus: TextView

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            viewModel.prcFileUri.value = it
            tvFileStatus.text = "File selected: PRC_License.pdf"
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val etBio = view.findViewById<EditText>(R.id.etClinicalBio)
        val etRate = view.findViewById<EditText>(R.id.etHourlyRate)
        val btnUpload = view.findViewById<Button>(R.id.btnUploadPrc)
        val btnNext = view.findViewById<Button>(R.id.btnDoctorNext)
        tvFileStatus = view.findViewById(R.id.tvPrcFileName)

        btnUpload.setOnClickListener { picker.launch("application/pdf") }

        btnNext.setOnClickListener {
            val bio = etBio.text.toString().trim()
            val rate = etRate.text.toString().trim()
            val fileUri = viewModel.prcFileUri.value

            // Validation: Ensure all doctor fields are filled
            if (bio.isEmpty() || rate.isEmpty() || fileUri == null) {
                Toast.makeText(requireContext(), "Bio, Rate, and PRC License are mandatory", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.clinicalBio.value = bio
            viewModel.hourlyRate.value = rate
            (activity as? RegisterActivity)?.showStep3()
        }
    }
}