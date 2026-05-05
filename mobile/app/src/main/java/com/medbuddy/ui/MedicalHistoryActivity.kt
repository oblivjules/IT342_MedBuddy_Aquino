package com.medbuddy.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.medbuddy.api.RetrofitClient
import com.medbuddy.databinding.ActivityMedicalHistoryBinding
import com.medbuddy.repository.MedicalRecordRepository
import com.medbuddy.viewmodel.MedicalRecordViewModel
import com.medbuddy.viewmodel.MedicalRecordViewModelFactory

class MedicalHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMedicalHistoryBinding
    private lateinit var viewModel: MedicalRecordViewModel
    private lateinit var adapter: MedicalRecordAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMedicalHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViewModel()
        setupAdapter()
        observeState()
        viewModel.loadRecords()
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
    }

    private fun setupAdapter() {
        adapter = MedicalRecordAdapter { record ->
            val intent = Intent(this, MedicalRecordDetailActivity::class.java)
            intent.putExtra("recordId", record.id)
            startActivity(intent)
        }
        binding.recyclerRecords.layoutManager = LinearLayoutManager(this)
        binding.recyclerRecords.adapter = adapter
    }

    private fun observeState() {
        lifecycleScope.launchWhenStarted {
            viewModel.recordsState.collect { state ->
                binding.progressBar.visibility = if (state.loading) View.VISIBLE else View.GONE

                if (state.items.isEmpty() && !state.loading) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "No medical records found"
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    adapter.submitList(state.items)
                }

                state.error?.let {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = it
                }
            }
        }
    }
}
