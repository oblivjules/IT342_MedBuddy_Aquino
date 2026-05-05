package com.medbuddy.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.medbuddy.api.RetrofitClient
import com.medbuddy.databinding.ActivityCreateRecordBinding
import com.medbuddy.repository.MedicalRecordRepository
import com.medbuddy.viewmodel.MedicalRecordViewModel
import com.medbuddy.viewmodel.MedicalRecordViewModelFactory

class CreateRecordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateRecordBinding
    private lateinit var viewModel: MedicalRecordViewModel
    private var appointmentId: Long = 0
    private var selectedFileUri: Uri? = null

    private fun buildPrescriptionSummary(): String {
        val parts = mutableListOf<String>()

        val medicine = binding.etMedicineName.text.toString().trim()
        val dosage = binding.etDosage.text.toString().trim()
        val route = binding.etRoute.text.toString().trim()
        val frequency = binding.etFrequency.text.toString().trim()
        val duration = binding.etDuration.text.toString().trim()
        val notes = binding.etPrescriptionNotes.text.toString().trim()

        if (medicine.isNotEmpty()) parts.add("Medicine: $medicine")
        if (dosage.isNotEmpty()) parts.add("Dosage: $dosage")
        if (route.isNotEmpty()) parts.add("Route: $route")
        if (frequency.isNotEmpty()) parts.add("Frequency: $frequency")
        if (duration.isNotEmpty()) parts.add("Duration: $duration")
        if (notes.isNotEmpty()) parts.add("Notes: $notes")

        return parts.joinToString(" | ")
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedFileUri = it
            binding.tvFileName.text = "File selected: ${it.lastPathSegment}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appointmentId = intent.getLongExtra("appointmentId", 0)

        setupToolbar()
        setupViewModel()
        setupListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupViewModel() {
        val repository = MedicalRecordRepository(
            RetrofitClient.getInstance(applicationContext).apiService
        )
        val factory = MedicalRecordViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory).get(MedicalRecordViewModel::class.java)

        lifecycleScope.launchWhenStarted {
            viewModel.createSuccess.collect { success ->
                if (success == true) {
                    android.widget.Toast.makeText(
                        this@CreateRecordActivity,
                        "Record created successfully",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    finish()
                } else if (success == false) {
                    android.widget.Toast.makeText(
                        this@CreateRecordActivity,
                        "Failed to create record",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnChooseFile.setOnClickListener {
            filePickerLauncher.launch("application/pdf")
        }

        binding.btnSubmit.setOnClickListener {
            submitRecord()
        }
    }

    private fun submitRecord() {
        val diagnosis = binding.etDiagnosis.text.toString().trim()

        if (diagnosis.isEmpty()) {
            binding.etDiagnosis.error = "Diagnosis is required"
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        val medicineName = binding.etMedicineName.text.toString().trim().ifBlank { null }
        val dosage = binding.etDosage.text.toString().trim().ifBlank { null }
        val route = binding.etRoute.text.toString().trim().ifBlank { null }
        val frequency = binding.etFrequency.text.toString().trim().ifBlank { null }
        val duration = binding.etDuration.text.toString().trim().ifBlank { null }
        val prescriptionNotes = binding.etPrescriptionNotes.text.toString().trim().ifBlank { null }

        viewModel.createRecord(
            appointmentId = appointmentId,
            diagnosis = diagnosis,
            prescriptionDetails = buildPrescriptionSummary().ifBlank { null },
            medicineName = medicineName,
            dosage = dosage,
            route = route,
            frequency = frequency,
            duration = duration,
            prescriptionNotes = prescriptionNotes
        )
    }
}
