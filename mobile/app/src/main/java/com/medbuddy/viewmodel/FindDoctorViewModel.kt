package com.medbuddy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.dto.DoctorDto
import kotlinx.coroutines.launch

/**
 * ViewModel for Find Doctor screen.
 * Manages doctor list with search and specialization filtering.
 */
class FindDoctorViewModel(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {

    private val _allDoctors = MutableLiveData<List<DoctorDto>>(emptyList())
    val allDoctors: LiveData<List<DoctorDto>> = _allDoctors

    private val _specializations = MutableLiveData<List<String>>(emptyList())
    val specializations: LiveData<List<String>> = _specializations

    private val _filteredDoctors = MutableLiveData<List<DoctorDto>>(emptyList())
    val filteredDoctors: LiveData<List<DoctorDto>> = _filteredDoctors

    private val _searchQuery = MutableLiveData("")
    val searchQuery: LiveData<String> = _searchQuery

    private val _selectedSpecialization = MutableLiveData("All")
    val selectedSpecialization: LiveData<String> = _selectedSpecialization

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        loadDoctors()
    }

    fun loadDoctors() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val doctors = apiService.getDoctors().bodyOrThrow()
                _allDoctors.value = doctors
                
                // Extract distinct specializations
                val specs = doctors
                    .mapNotNull { it.specialization ?: it.specializations?.firstOrNull() }
                    .distinct()
                    .sorted()
                
                _specializations.value = listOf("All") + specs
                _selectedSpecialization.value = "All"
                
                applyFilters()
                
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load doctors"
                _filteredDoctors.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    fun setSelectedSpecialization(spec: String) {
        _selectedSpecialization.value = spec
        applyFilters()
    }

    private fun applyFilters() {
        val query = _searchQuery.value?.lowercase() ?: ""
        val spec = _selectedSpecialization.value ?: "All"
        
        val filtered = _allDoctors.value?.filter { doctor ->
            val nameMatch = "${doctor.firstName} ${doctor.lastName}".lowercase().contains(query)
            val specMatch = spec == "All" || 
                (doctor.specialization?.equals(spec, ignoreCase = true) == true ||
                 doctor.specializations?.any { it.equals(spec, ignoreCase = true) } == true)
            
            nameMatch && specMatch
        } ?: emptyList()
        
        _filteredDoctors.value = filtered
    }
}

class FindDoctorViewModelFactory(
    private val application: Application,
    private val apiService: ApiService
) : androidx.lifecycle.ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return FindDoctorViewModel(application, apiService) as T
    }
}
