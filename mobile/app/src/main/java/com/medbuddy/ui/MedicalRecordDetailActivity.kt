package com.medbuddy.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.databinding.ActivityMedicalRecordDetailBinding
import com.medbuddy.repository.MedicalRecordRepository
import com.medbuddy.repository.PaymentRepository
import com.medbuddy.viewmodel.MedicalRecordViewModel
import com.medbuddy.viewmodel.MedicalRecordViewModelFactory
import com.medbuddy.viewmodel.PaymentViewModel
import com.medbuddy.viewmodel.PaymentViewModelFactory
import androidx.lifecycle.lifecycleScope

class MedicalRecordDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMedicalRecordDetailBinding
    private lateinit var recordViewModel: MedicalRecordViewModel
    private lateinit var paymentViewModel: PaymentViewModel
    private lateinit var fileAdapter: MedicalRecordFileAdapter
    private var appointmentId: Long = 0
    private var paymentStatus: String? = null

    private fun hasDrugInfo(drug: com.medbuddy.dto.DrugInfoResponse?): Boolean {
        return drug?.available == true && (
            !drug.indications.isNullOrBlank() ||
                !drug.warnings.isNullOrBlank() ||
                !drug.dosageAdministration.isNullOrBlank() ||
                !drug.description.isNullOrBlank()
            )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMedicalRecordDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recordId = intent.getLongExtra("recordId", 0)
        appointmentId = intent.getLongExtra("appointmentId", 0)

        setupToolbar()
        setupViewModels()
        setupAdapter()
        observeStates()
        recordViewModel.loadRecordDetail(recordId)

        if (appointmentId > 0) {
            paymentViewModel.loadPaymentStatus(appointmentId)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupViewModels() {
        val recordRepository = MedicalRecordRepository(
            RetrofitClient.getInstance(applicationContext).apiService
        )
        val recordFactory = MedicalRecordViewModelFactory(recordRepository)
        recordViewModel = ViewModelProvider(this, recordFactory).get(MedicalRecordViewModel::class.java)

        val paymentRepository = PaymentRepository(
            RetrofitClient.getInstance(applicationContext).apiService
        )
        val paymentFactory = PaymentViewModelFactory(paymentRepository)
        paymentViewModel = ViewModelProvider(this, paymentFactory).get(PaymentViewModel::class.java)
    }

    private fun setupAdapter() {
        fileAdapter = MedicalRecordFileAdapter { file ->
            openOrDownloadFile(file.url, file.fileName)
        }
        binding.recyclerFiles.layoutManager = LinearLayoutManager(this)
        binding.recyclerFiles.adapter = fileAdapter
    }

    private fun observeStates() {
        lifecycleScope.launchWhenStarted {
            recordViewModel.detailState.collect { state ->
                binding.progressBar.visibility = if (state.loading) View.VISIBLE else View.GONE

                state.record?.let { record ->
                    binding.tvDiagnosis.text = record.diagnosis
                    binding.tvMedicineName.text = record.medicineName ?: "N/A"
                    binding.tvDosage.text = record.dosage ?: "N/A"
                    binding.tvRoute.text = record.route ?: "N/A"
                    binding.tvFrequency.text = record.frequency ?: "N/A"
                    binding.tvDuration.text = record.duration ?: "N/A"
                    binding.tvPrescriptionNotes.text = record.prescriptionNotes ?: "N/A"
                }

                fileAdapter.submitList(state.files)

                if (state.files.isEmpty() && !state.loading) {
                    binding.tvNoFiles.visibility = View.VISIBLE
                } else {
                    binding.tvNoFiles.visibility = View.GONE
                }

                state.error?.let {
                    binding.tvError.visibility = View.VISIBLE
                    binding.tvError.text = it
                }
            }
        }

        lifecycleScope.launchWhenStarted {
            recordViewModel.detailState.collect { state ->
                if (state.drugInfoLoading) {
                    binding.tvDrugUnavailable.visibility = View.GONE
                    binding.cardDrugInfo.visibility = View.GONE
                    binding.progressDrugInfo.visibility = View.VISIBLE
                } else if (hasDrugInfo(state.drugInfo)) {
                    val drug = state.drugInfo!!
                    binding.progressDrugInfo.visibility = View.GONE
                    binding.tvDrugUnavailable.visibility = View.GONE
                    binding.cardDrugInfo.visibility = View.VISIBLE
                    binding.tvDrugIndications.text = drug.indications ?: "N/A"
                    binding.tvDrugWarnings.text = drug.warnings ?: "N/A"
                    binding.tvDrugDosage.text = drug.dosageAdministration ?: "N/A"
                    binding.tvDrugDescription.text = drug.description ?: "N/A"
                } else {
                    binding.progressDrugInfo.visibility = View.GONE
                    binding.cardDrugInfo.visibility = View.GONE
                    binding.tvDrugUnavailable.visibility = View.VISIBLE
                }
            }
        }

        lifecycleScope.launchWhenStarted {
            paymentViewModel.paymentState.collect { state ->
                state.payment?.let { payment ->
                    paymentStatus = payment.status
                    if (payment.status != "PAID") {
                        binding.cardLocked.visibility = View.VISIBLE
                        binding.cardContent.alpha = 0.5f
                    } else {
                        binding.cardLocked.visibility = View.GONE
                        binding.cardContent.alpha = 1f
                    }
                }
            }
        }
    }

    private fun openOrDownloadFile(url: String?, fileName: String) {
        if (!url.isNullOrEmpty()) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            startActivity(intent)
        }
    }
}
