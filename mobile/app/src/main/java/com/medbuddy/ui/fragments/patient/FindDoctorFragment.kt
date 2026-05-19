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
        binding.dropdownSpecialization.setOnItemClickListener { _, _, position, _ ->
            val selected = binding.dropdownSpecialization.adapter?.getItem(position)?.toString().orEmpty()
            selectedSpecialization = if (selected.equals("All Specializations", ignoreCase = true) || selected.isBlank()) "ALL" else selected
            applyFilters()
        }
        binding.dropdownSpecialization.doAfterTextChanged { text ->
            val selected = text?.toString().orEmpty()
            val newFilter = if (selected.equals("All Specializations", ignoreCase = true) || selected.isBlank()) "ALL" else selected
            if (newFilter != selectedSpecialization) {
                selectedSpecialization = newFilter
                applyFilters()
            }
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
                        doctor.specializations?.forEach { add(it) }
                        doctor.specialization?.let { add(it) }
                    }
                }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()

                binding.dropdownSpecialization.setAdapter(
                    ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_list_item_1,
                        specializations,
                    )
                )
                binding.dropdownSpecialization.setText("All Specializations", false)
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
        val specializationFilter = selectedSpecialization
        val filtered = allDoctors.filter { doctor ->
            val name = listOfNotNull(doctor.firstName, doctor.lastName).joinToString(" ").lowercase()
            val email = doctor.email.orEmpty().lowercase()
            val specializations = buildList {
                doctor.specializations?.forEach { add(it) }
                doctor.specialization?.let { add(it) }
            }
            val matchesQuery = query.isBlank() || name.contains(query) || email.contains(query)
            val matchesSpecialization = specializationFilter == "ALL" || specializations.any { it.equals(specializationFilter, ignoreCase = true) }
            matchesQuery && matchesSpecialization
        }

        binding.tvEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        doctorAdapter.submitList(filtered)
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
