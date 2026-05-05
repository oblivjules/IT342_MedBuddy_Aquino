package com.medbuddy.ui.fragments.patient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.medbuddy.R
import com.medbuddy.api.ApiErrorMapper
import com.medbuddy.api.RetrofitClient
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.FragmentFindDoctorBinding
import com.medbuddy.dto.DoctorDto
import com.medbuddy.ui.DoctorAdapter
import com.medbuddy.ui.SessionUi
import kotlinx.coroutines.launch

class FindDoctorFragment : Fragment() {

    private lateinit var binding: FragmentFindDoctorBinding
    private lateinit var adapter: DoctorAdapter
    private lateinit var tokenManager: TokenManager
    private var allDoctors: List<DoctorDto> = emptyList()
    private var activeSpecialization: String = "ALL"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFindDoctorBinding.inflate(inflater, container, false)
        tokenManager = TokenManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFilters()
        setupSearch()
        setupSwipeRefresh()
        binding.btnRetry.setOnClickListener { loadDoctors() }
        loadDoctors()
    }

    private fun setupRecyclerView() {
        adapter = DoctorAdapter { doctor ->
            // Navigate to book appointment
            val fragment = BookAppointmentFragment.newInstance(doctor.id, doctor.firstName + " " + doctor.lastName)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit()
        }

        binding.rvDoctors.adapter = adapter
        binding.rvDoctors.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupFilters() {
        binding.chipAll.setOnClickListener { activeSpecialization = "ALL"; applyFilters() }
        binding.chipCardiology.setOnClickListener { activeSpecialization = "Cardiology"; applyFilters() }
        binding.chipDermatology.setOnClickListener { activeSpecialization = "Dermatology"; applyFilters() }
        binding.chipOrthopedics.setOnClickListener { activeSpecialization = "Orthopedics"; applyFilters() }
        binding.chipNeurology.setOnClickListener { activeSpecialization = "Neurology"; applyFilters() }
        binding.chipPediatrics.setOnClickListener { activeSpecialization = "Pediatrics"; applyFilters() }
    }

    private fun setupSearch() {
        binding.etSearch.doAfterTextChanged {
            applyFilters()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { loadDoctors() }
    }

    private fun loadDoctors() {
        if (!tokenManager.isLoggedIn()) {
            SessionUi.redirectToLogin(this)
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmptyState.visibility = View.GONE
        binding.btnRetry.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                allDoctors = RetrofitClient.getInstance(context ?: return@launch)
                    .apiService
                    .getAllDoctors()

                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                applyFilters()
            } catch (e: Throwable) {
                if (SessionUi.handleAuthError(this@FindDoctorFragment, e)) {
                    return@launch
                }
                val safeContext = context ?: return@launch
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.tvEmptyState.text = ApiErrorMapper.toUserMessage(safeContext, e)
                binding.btnRetry.visibility = View.VISIBLE
                Toast.makeText(
                    safeContext,
                    ApiErrorMapper.toUserMessage(safeContext, e),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun applyFilters() {
        val query = binding.etSearch.text?.toString()?.trim().orEmpty().lowercase()
        val filtered = allDoctors.filter { doctor ->
            val name = "${doctor.firstName} ${doctor.lastName}".lowercase()
            val specs = (doctor.specializations ?: listOfNotNull(doctor.specialization)).joinToString(",")
            val matchesQuery = query.isBlank() || name.contains(query) || doctor.email.lowercase().contains(query)
            val matchesSpec = activeSpecialization == "ALL" || specs.contains(activeSpecialization, ignoreCase = true)
            matchesQuery && matchesSpec
        }

        binding.tvEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        adapter.submitList(filtered)
    }
}
