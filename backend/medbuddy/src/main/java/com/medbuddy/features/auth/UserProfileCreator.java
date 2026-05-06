package com.medbuddy.features.auth;

import java.util.Set;

import com.medbuddy.features.auth.RegisterRequest;
import com.medbuddy.shared.model.Role;
import com.medbuddy.shared.model.Specialization;
import com.medbuddy.shared.model.User;

public interface UserProfileCreator {

    boolean supports(Role role);

    void createProfile(User user, RegisterRequest request, Set<Specialization> doctorSpecializations);
}
