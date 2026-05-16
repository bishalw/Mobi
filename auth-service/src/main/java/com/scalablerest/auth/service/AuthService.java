package com.scalablerest.auth.service;

import com.scalablerest.auth.dto.AuthResponse;
import com.scalablerest.auth.dto.LoginRequest;
import com.scalablerest.auth.dto.RegisterRequest;

public interface AuthService {

    void register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
