package com.mobi.auth.service;

import com.mobi.auth.dto.AuthResponse;
import com.mobi.auth.dto.LoginRequest;
import com.mobi.auth.dto.RegisterResponse;
import com.mobi.auth.dto.RegisterRequest;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
