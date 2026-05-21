package com.sidarorman.ecommerce.user.service;

import com.sidarorman.ecommerce.user.dto.AuthResponse;
import com.sidarorman.ecommerce.user.dto.LoginRequest;
import com.sidarorman.ecommerce.user.dto.RegisterRequest;
import com.sidarorman.ecommerce.user.exception.InvalidCredentialsException;
import com.sidarorman.ecommerce.user.exception.UserAlreadyExistsException;
import com.sidarorman.ecommerce.user.model.User;
import com.sidarorman.ecommerce.user.repository.UserRepository;
import com.sidarorman.ecommerce.user.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username '" + request.getUsername() + "' is already taken");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role("ROLE_USER")
                .build();

        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole());

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole());

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }
}
