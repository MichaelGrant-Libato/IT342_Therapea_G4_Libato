package com.therapea.app.features.therapists

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.therapea.app.R
import com.therapea.app.features.therapists.adapter.PatientAdapter
import com.therapea.app.features.therapists.adapter.RecordAdapter
import com.therapea.app.features.therapists.model.Patient
import com.therapea.app.features.therapists.model.SessionRecord
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class PatientsActivity : Activity() {

    private lateinit var recyclerPatients: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var txtEmpty: TextView
    private lateinit var etSearch: EditText

    private lateinit var patientAdapter: PatientAdapter

    private val patients = mutableListOf<Patient>()
    private val filteredPatients = mutableListOf<Patient>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patients)

        recyclerPatients = findViewById(R.id.recyclerPatients)
        progressBar = findViewById(R.id.progressBar)
        txtEmpty = findViewById(R.id.txtEmpty)
        etSearch = findViewById(R.id.etSearch)

        patientAdapter = PatientAdapter(filteredPatients) { patient ->
            fetchPatientRecords(patient)
        }

        recyclerPatients.layoutManager = LinearLayoutManager(this)
        recyclerPatients.adapter = patientAdapter

        fetchPatients()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterPatients(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun fetchPatients() {
        progressBar.visibility = View.VISIBLE
        txtEmpty.visibility = View.GONE

        val prefs = getSharedPreferences("TheraPeaSession", MODE_PRIVATE)
        val sessionData = prefs.getString("user_data", null)

        var email = ""
        if (sessionData != null) {
            try {
                val userJson = JSONObject(sessionData)
                email = userJson.optString("email", "")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (email.isBlank()) {
            Toast.makeText(this, "Doctor email not found in app storage.", Toast.LENGTH_LONG).show()
            progressBar.visibility = View.GONE
            return
        }

        val url = "http://10.0.2.2:8083/api/patients/doctor?email=$email"
        val request = Request.Builder().url(url).build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@PatientsActivity, "Failed to connect to server.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""

                runOnUiThread {
                    progressBar.visibility = View.GONE

                    if (response.isSuccessful && body.isNotBlank()) {
                        try {
                            patients.clear()

                            // Automatically handle whether the backend returned [...] or {"patients": [...]}
                            val array: JSONArray = if (body.trim().startsWith("[")) {
                                JSONArray(body)
                            } else {
                                val json = JSONObject(body)
                                json.optJSONArray("patients") ?: json.optJSONArray("data") ?: JSONArray()
                            }

                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)
                                patients.add(
                                    Patient(
                                        id = obj.optString("id"),
                                        // Web shows "test100" which might just be stored under "name" instead of "fullName"
                                        name = obj.optString("name", obj.optString("fullName", "Unknown Patient")),
                                        email = obj.optString("email", "No email provided"),
                                        status = "Active",
                                        risk = "Low"
                                    )
                                )
                            }

                            filteredPatients.clear()
                            filteredPatients.addAll(patients)
                            patientAdapter.notifyDataSetChanged()

                            txtEmpty.visibility = if (patients.isEmpty()) View.VISIBLE else View.GONE

                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(this@PatientsActivity, "Error parsing patient data from server.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this@PatientsActivity, "Server Error: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun filterPatients(query: String) {

        filteredPatients.clear()

        filteredPatients.addAll(
            patients.filter {
                it.name.lowercase().contains(query.lowercase())
            }
        )

        patientAdapter.notifyDataSetChanged()
    }

    private fun fetchPatientRecords(patient: Patient) {

        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_patient_records, null)

        val dialogRecycler =
            dialogView.findViewById<RecyclerView>(R.id.recyclerRecords)

        val dialogProgress =
            dialogView.findViewById<ProgressBar>(R.id.progressRecords)

        val txtNoRecords =
            dialogView.findViewById<TextView>(R.id.txtNoRecords)

        val txtTitle =
            dialogView.findViewById<TextView>(R.id.txtPatientTitle)

        txtTitle.text = patient.name

        val records = mutableListOf<SessionRecord>()

        val adapter = RecordAdapter(records)

        dialogRecycler.layoutManager = LinearLayoutManager(this)
        dialogRecycler.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.show()

        val url =
            "http://10.0.2.2:8083/api/progress/patient?email=${patient.email}"

        val request = Request.Builder()
            .url(url)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {

                runOnUiThread {

                    dialogProgress.visibility = View.GONE

                    Toast.makeText(
                        this@PatientsActivity,
                        "Failed to load records",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {

                val body = response.body?.string()

                runOnUiThread {

                    dialogProgress.visibility = View.GONE

                    if (response.isSuccessful && body != null) {

                        val json = JSONObject(body)

                        if (json.getBoolean("success")) {

                            val array = json.getJSONArray("records")

                            for (i in 0 until array.length()) {

                                val obj = array.getJSONObject(i)

                                records.add(
                                    SessionRecord(
                                        id = obj.optString("id"),
                                        date = obj.optString("date"),
                                        type = obj.optString("type"),
                                        notes = obj.optString("notes")
                                    )
                                )
                            }

                            adapter.notifyDataSetChanged()

                            txtNoRecords.visibility =
                                if (records.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }
            }
        })
    }
}