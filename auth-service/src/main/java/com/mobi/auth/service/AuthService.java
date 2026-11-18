package com.mobi.auth.service;

import com.mobi.auth.dto.LoginRequest;
import com.mobi.auth.dto.LoginResult;
import com.mobi.auth.dto.RegisterRequest;
import com.mobi.auth.dto.RegisterResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    LoginResult login(LoginRequest request, String userAgent);

    LoginResult refresh(String refreshToken, String userAgent);

    void logout(String refreshToken);

    void logoutEverywhere(Long userId);
}
