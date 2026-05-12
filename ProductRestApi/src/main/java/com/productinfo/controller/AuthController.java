package com.productinfo.controller;

import com.productinfo.dto.AuthRequest;
import com.productinfo.dto.AuthResponse;
import com.productinfo.entity.User;
import com.productinfo.security.JwtUtil;
import com.productinfo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public String registerUser(@RequestBody AuthRequest request)
    {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());

        userRepository.save(user);
        return "USER REGISTERED!";
    }
    @PostMapping("/login")
    public AuthResponse loginUser(@RequestBody AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername()).orElseThrow();

        if(passwordEncoder.matches(request.getPassword(), user.getPassword()))
        {
            String token = jwtUtil.generateToken(user.getUsername(), user.getRole());

            return new AuthResponse(token,user.getUsername(),user.getRole());
        }
        throw new RuntimeException("INVALID CREDENTIALS");
    }

}
