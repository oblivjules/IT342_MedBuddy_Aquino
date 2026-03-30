package com.medbuddy.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.medbuddy.R
import com.medbuddy.api.ApiErrorMapper
import com.medbuddy.api.RetrofitClient
import com.medbuddy.databinding.ActivityFindDoctorBinding
import kotlinx.coroutines.launch

class FindDoctorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFindDoctorBinding
    private val adapter = DoctorAdapter { doctor ->
        startActivity(Intent(this, BookAppointmentActivity::class.java).apply {
            putExtra("doctorId", doctor.id)
            putExtra("doctorName", "Dr. ${doctor.firstName} ${doctor.lastName}")
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFindDoctorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerDoctors.layoutManager = LinearLayoutManager(this)
        binding.recyclerDoctors.adapter = adapter

        loadDoctors()
    }

    private fun loadDoctors() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val doctors = RetrofitClient.getInstance(applicationContext).apiService.getDoctors()
                adapter.submitList(doctors)
                if (doctors.isEmpty()) {
                    binding.tvEmpty.text = getString(R.string.label_no_data)
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                }
            } catch (e: Throwable) {
                binding.tvEmpty.text = ApiErrorMapper.toUserMessage(this@FindDoctorActivity, e)
                binding.tvEmpty.visibility = View.VISIBLE
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
}

