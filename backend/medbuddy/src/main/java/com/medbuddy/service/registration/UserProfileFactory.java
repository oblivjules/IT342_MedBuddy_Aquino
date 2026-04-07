package com.medbuddy.service.registration;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.medbuddy.dto.RegisterRequest;
import com.medbuddy.model.Specialization;
import com.medbuddy.model.User;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserProfileFactory {

    private final List<UserProfileCreator> profileCreators;

    public void createProfile(User user, RegisterRequest request, Set<Specialization> doctorSpecializations) {
        UserProfileCreator creator = profileCreators.stream()
                .filter(candidate -> candidate.supports(request.getRole()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported role: " + request.getRole()));

        creator.createProfile(user, request, doctorSpecializations);
    }
}
