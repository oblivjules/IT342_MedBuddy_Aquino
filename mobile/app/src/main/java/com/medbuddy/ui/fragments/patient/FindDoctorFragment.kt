package com.medbuddy.ui.fragments.patient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.FragmentFindDoctorRefinedBinding
import com.medbuddy.dto.DoctorDto
import com.medbuddy.repository.RatingRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class FindDoctorFragment : Fragment() {

    private lateinit var binding: FragmentFindDoctorRefinedBinding
    private lateinit var tokenManager: TokenManager
    private lateinit var doctorAdapter: PatientDoctorAdapter
    private var allDoctors: List<DoctorDto> = emptyList()
    private var selectedSpecialization: String = "ALL"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentFindDoctorRefinedBinding.inflate(inflater, container, false)
        tokenManager = TokenManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        doctorAdapter = PatientDoctorAdapter(
            onCardClick = { openDoctorProfile(it) },
            onBookClick = { openBooking(it) },
        )

        binding.rvDoctors.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDoctors.adapter = doctorAdapter

        binding.etSearch.doAfterTextChanged { applyFilters() }
        binding.dropdownSpecialization.setOnClickListener {
            binding.dropdownSpecialization.showDropDown()
        }
        binding.dropdownSpecialization.setOnItemClickListener { _, _, position, _ ->
            val selected = binding.dropdownSpecialization.adapter?.getItem(position)?.toString().orEmpty()
            selectedSpecialization = selected.toFilterToken()
            applyFilters()
        }

        loadDoctors()
    }

    private fun loadDoctors() {
        if (!tokenManager.isLoggedIn()) return

        binding.progressBar.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.GONE
        binding.tvEmptyState.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getInstance(requireContext()).apiService
                val doctors = apiService.getDoctors().bodyOrThrow()
                val ratingRepository = RatingRepository(apiService)

                allDoctors = coroutineScope {
                    doctors.map { doctor ->
                        async {
                            val avg = runCatching { ratingRepository.getAverageRating(doctor.id).averageRating }.getOrNull()
                            doctor.copy(averageRating = avg)
                        }
                    }.map { it.await() }
                }

                val specializations = buildList {
                    add("All Specializations")
                    allDoctors.forEach { doctor ->
                        addAll(extractDoctorSpecializations(doctor))
                    }
                }
                    .distinctBy { it.toFilterToken() }
                    .sortedBy { if (it.equals("All Specializations", ignoreCase = true)) "" else it.lowercase() }

                binding.dropdownSpecialization.setAdapter(
                    ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_list_item_1,
                        specializations,
                    )
                )
                binding.dropdownSpecialization.setText("All", false)
                selectedSpecialization = "ALL"
                applyFilters()
            } catch (_: Throwable) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.tvEmptyState.text = "No doctors found"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.scrollContent.visibility = View.VISIBLE
            }
        }
    }

    private fun applyFilters() {
        val query = binding.etSearch.text?.toString().orEmpty().trim().lowercase()
        val specializationFilter = selectedSpecialization.toFilterToken()
        val filtered = allDoctors.filter { doctor ->
            val name = listOfNotNull(doctor.firstName, doctor.lastName).joinToString(" ").lowercase()
            val email = doctor.email.orEmpty().lowercase()
            val specializations = extractDoctorSpecializations(doctor)
            val matchesQuery = query.isBlank() || name.contains(query) || email.contains(query)
            val matchesSpecialization = specializationFilter == "ALL" || specializations.any { it.toFilterToken() == specializationFilter }
            matchesQuery && matchesSpecialization
        }

        binding.tvEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        doctorAdapter.submitList(filtered)
    }

    private fun extractDoctorSpecializations(doctor: DoctorDto): List<String> {
        val rawValues = buildList {
            doctor.specializations?.forEach { add(it) }
            doctor.specialization?.let { add(it) }
        }

        return rawValues
            .flatMap { value -> value.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    private fun String.toFilterToken(): String {
        val normalized = trim().replace(Regex("\\s+"), " ")
        return if (
            normalized.isBlank() ||
            normalized.equals("All", ignoreCase = true) ||
            normalized.equals("All Specializations", ignoreCase = true)
        ) {
            "ALL"
        } else {
            normalized.lowercase()
        }
    }

    private fun openDoctorProfile(doctor: DoctorDto) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, DoctorProfileFragment.newInstance(doctor))
            .addToBackStack(null)
            .commit()
    }

    private fun openBooking(doctor: DoctorDto) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, BookAppointmentFragment.newInstance(doctor))
            .addToBackStack(null)
            .commit()
    }
}
