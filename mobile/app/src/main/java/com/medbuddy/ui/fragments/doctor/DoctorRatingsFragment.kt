package com.medbuddy.ui.fragments.doctor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.medbuddy.api.RetrofitClient
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.FragmentDoctorRatingsBinding
import com.medbuddy.dto.UserDto
import com.medbuddy.repository.FeedbackRepository
import com.medbuddy.ui.fragments.patient.DoctorFeedbackAdapter
import kotlinx.coroutines.launch

class DoctorRatingsFragment : Fragment() {

    private lateinit var binding: FragmentDoctorRatingsBinding
    private lateinit var adapter: DoctorFeedbackAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDoctorRatingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = DoctorFeedbackAdapter()
        binding.rvFeedback.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFeedback.adapter = adapter
        loadFeedback()
    }

    private fun loadFeedback() {
        binding.progressBar.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val tokenManager = TokenManager(requireContext())
                val user = runCatching {
                    Gson().fromJson(tokenManager.getUserJson(), UserDto::class.java)
                }.getOrNull()
                val profileId = user?.profileId ?: return@launch

                val repository = FeedbackRepository(RetrofitClient.getInstance(requireContext()).apiService)
                val feedback = repository.getDoctorFeedback(profileId)

                adapter.submitList(feedback)
                binding.tvEmptyState.visibility = if (feedback.isEmpty()) View.VISIBLE else View.GONE
            } catch (_: Throwable) {
                binding.tvEmptyState.visibility = View.VISIBLE
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.scrollContent.visibility = View.VISIBLE
            }
        }
    }
}
