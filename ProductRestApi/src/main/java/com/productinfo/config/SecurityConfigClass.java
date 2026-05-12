package com.productinfo.config;

import com.productinfo.security.JwtFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfigClass {
    @Autowired
    private JwtFilter jwtFilter;
    //PasswordEncoder - BCryptpassword encoder
    @Bean
    public PasswordEncoder passwordEncode() {
        return new BCryptPasswordEncoder();
    }

    //securityFilterChain - HttpSecurity (main section security)
//securityFilterChain - HttpSecurity (main section security)
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)throws Exception
    {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests((authorize)->
                        authorize.requestMatchers("/auth/**").permitAll()
                                .requestMatchers("/productapi/**").hasAuthority("ADMIN")
                                .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    //UserDetailService - (to create authenticated users)
    @Bean
    UserDetailsService userDetailsService(){

        UserDetails admin = User.builder()
                .username("Laxmi")
                .password(passwordEncode().encode("apex@123"))
                .roles("ADMIN")
                .build();
        UserDetails manager = User.builder()
                .username("Sidharth")
                .password(passwordEncode().encode("sidx@123"))
                .roles("MANAGER")
                .build();

        UserDetails user1 = User.builder()
                .username("Prasanna")
                .password(passwordEncode().encode("test@123"))
                .roles("USER")
                .build();
        UserDetails user2 = User.builder()
                .username("Himanshi")
                .password(passwordEncode().encode("test@123"))
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(admin,manager,user1,user2);
    }

}
