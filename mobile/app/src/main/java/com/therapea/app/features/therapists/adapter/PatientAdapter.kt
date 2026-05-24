package com.therapea.app.features.therapists.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView // <-- Updated import
import androidx.recyclerview.widget.RecyclerView
import com.therapea.app.R
import com.therapea.app.features.therapists.model.Patient

class PatientAdapter(
    private val patients: List<Patient>,
    private val onViewClick: (Patient) -> Unit
) : RecyclerView.Adapter<PatientAdapter.PatientViewHolder>() {

    inner class PatientViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val txtName: TextView = view.findViewById(R.id.txtName)
        val txtEmail: TextView = view.findViewById(R.id.txtEmail)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
        val txtRisk: TextView = view.findViewById(R.id.txtRisk)
        val btnView: TextView = view.findViewById(R.id.btnView) // <-- Fixed! Changed from Button to TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatientViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_patient, parent, false)

        return PatientViewHolder(view)
    }

    override fun getItemCount(): Int = patients.size

    override fun onBindViewHolder(holder: PatientViewHolder, position: Int) {

        val patient = patients[position]

        holder.txtName.text = patient.name
        holder.txtEmail.text = patient.email
        holder.txtStatus.text = patient.status
        holder.txtRisk.text = patient.risk

        holder.btnView.setOnClickListener {
            onViewClick(patient)
        }
    }
}