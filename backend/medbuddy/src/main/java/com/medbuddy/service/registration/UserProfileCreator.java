package com.medbuddy.service.registration;

import java.util.Set;

import com.medbuddy.dto.RegisterRequest;
import com.medbuddy.model.Role;
import com.medbuddy.model.Specialization;
import com.medbuddy.model.User;

public interface UserProfileCreator {

    boolean supports(Role role);

    void createProfile(User user, RegisterRequest request, Set<Specialization> doctorSpecializations);
}
