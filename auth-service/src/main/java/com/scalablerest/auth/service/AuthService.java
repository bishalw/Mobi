package com.scalablerest.auth.service;

import com.scalablerest.auth.dto.AuthResponse;
import com.scalablerest.auth.dto.LoginRequest;
import com.scalablerest.auth.dto.RegisterResponse;
import com.scalablerest.auth.dto.RegisterRequest;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
