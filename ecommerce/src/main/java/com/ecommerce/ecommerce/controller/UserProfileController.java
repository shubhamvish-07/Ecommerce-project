package com.ecommerce.ecommerce.controller;

import com.ecommerce.ecommerce.dto.DeliveryAddressRequest;
import com.ecommerce.ecommerce.dto.UserProfileResponse;
import com.ecommerce.ecommerce.entity.User;
import com.ecommerce.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.ecommerce.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/profile")
public class UserProfileController {

    private final UserRepository userRepository;

    public UserProfileController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public UserProfileResponse getProfile(Authentication authentication) {
        return toResponse(findUser(authentication.getName()));
    }

    @PutMapping
    public UserProfileResponse updateAddress(
            Authentication authentication,
            @Valid @RequestBody DeliveryAddressRequest request) {
        User user = findUser(authentication.getName());
        user.setRecipientName(request.getName().trim());
        user.setRecipientPhone(request.getPhone().trim());
        user.setDeliveryAddress(request.getAddressLine().trim());
        user.setDeliveryCity(request.getCity().trim());
        user.setDeliveryState(request.getState().trim());
        user.setDeliveryPostalCode(request.getPostalCode().trim());
        return toResponse(userRepository.save(user));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(
                user.getName(),
                user.getEmail(),
                user.getRecipientName(),
                user.getRecipientPhone(),
                user.getDeliveryAddress(),
                user.getDeliveryCity(),
                user.getDeliveryState(),
                user.getDeliveryPostalCode()
        );
    }
}
