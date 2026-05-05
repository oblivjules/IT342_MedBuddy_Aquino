package com.medbuddy.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.medbuddy.api.RetrofitClient
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.ActivityDoctorRatingsBinding
import com.medbuddy.dto.UserDto
import com.medbuddy.repository.RatingRepository
import com.medbuddy.viewmodel.RatingViewModel
import com.medbuddy.viewmodel.RatingViewModelFactory

class DoctorRatingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDoctorRatingsBinding
    private lateinit var viewModel: RatingViewModel
    private lateinit var adapter: RatingAdapter
    private var doctorId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDoctorRatingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        doctorId = intent.getLongExtra("doctorId", 0)
        if (doctorId == 0L) {
            // Try to get doctorId from current user
            val json = TokenManager(applicationContext).getUserJson()
            if (!json.isNullOrBlank()) {
                try {
                    val user = Gson().fromJson(json, UserDto::class.java)
                    doctorId = user.profileId ?: 0
                } catch (e: Exception) {
                    // Continue with doctorId = 0
                }
            }
        }

        setupToolbar()
        setupViewModel()
        setupAdapter()
        observeState()

        if (doctorId > 0) {
            viewModel.loadDoctorRatings(doctorId)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupViewModel() {
        val repository = RatingRepository(
            RetrofitClient.getInstance(applicationContext).apiService
        )
        val factory = RatingViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory).get(RatingViewModel::class.java)
    }

    private fun setupAdapter() {
        adapter = RatingAdapter()
        binding.recyclerRatings.layoutManager = LinearLayoutManager(this)
        binding.recyclerRatings.adapter = adapter
    }

    private fun observeState() {
        lifecycleScope.launchWhenStarted {
            viewModel.doctorRatingsState.collect { state ->
                binding.progressBar.visibility = if (state.loading) View.VISIBLE else View.GONE

                state.average?.let { avg ->
                    binding.ratingBarAverage.rating = avg.averageRating.toFloat()
                    binding.tvAverageScore.text = String.format("%.1f", avg.averageRating)
                    binding.tvTotalRatings.text = "(${avg.totalRatings} ratings)"
                }

                if (state.ratings.isEmpty() && !state.loading) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "No ratings yet"
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    adapter.submitList(state.ratings)
                }

                state.error?.let {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = it
                }
            }
        }
    }
}
