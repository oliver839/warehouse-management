package com.logistics.warehouse_management.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import com.logistics.warehouse_management.repository.UserRepository;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableMethodSecurity
@EnableScheduling
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/import/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/shipping-outbox/*/retry").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/stock-positions/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/stock-adjustments").hasAnyRole("ADMIN", "MANAGER", "WAREHOUSE_WORKER")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/stock-adjustments/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/stock-adjustments/*/approve", "/api/stock-adjustments/*/reject").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/items/**", "/api/warehouses/**", "/api/storage-locations/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/items/**", "/api/warehouses/**", "/api/storage-locations/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/items/**", "/api/warehouses/**", "/api/storage-locations/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/items/**", "/api/warehouses/**", "/api/storage-locations/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/projects/**", "/api/pick-orders/**", "/api/delivery-notes/**").hasAnyRole("ADMIN", "MANAGER", "WAREHOUSE_WORKER")
                        .requestMatchers(HttpMethod.GET, "/api/shipping-outbox/by-delivery-note/**").hasAnyRole("ADMIN", "MANAGER", "WAREHOUSE_WORKER")
                        .requestMatchers(HttpMethod.POST, "/api/shipping-outbox/process-due").hasAnyRole("ADMIN", "MANAGER", "WAREHOUSE_WORKER")
                        .requestMatchers(HttpMethod.POST, "/api/goods-receipts/**", "/api/pick-orders/*/start", "/api/pick-orders/*/lines/*/scan", "/api/pick-orders/*/lines/*/shortage", "/api/pick-orders/*/complete", "/api/delivery-notes/**").hasAnyRole("ADMIN", "WAREHOUSE_WORKER")
                        .requestMatchers(HttpMethod.POST, "/api/pick-orders/from-project/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/projects/**").hasAnyRole("ADMIN", "MANAGER", "WAREHOUSE_WORKER")
                        .requestMatchers(HttpMethod.PATCH, "/api/projects/**").hasAnyRole("ADMIN", "MANAGER", "WAREHOUSE_WORKER")
                        .requestMatchers(HttpMethod.DELETE, "/api/projects/**").hasRole("ADMIN")
                        .requestMatchers("/api/items/*/transactions").hasAnyRole("ADMIN", "MANAGER", "WAREHOUSE_WORKER")
                        .requestMatchers("/api/projects/**").hasAnyRole("ADMIN", "MANAGER", "WAREHOUSE_WORKER")
                        .anyRequest().authenticated())
                .httpBasic(basic -> {})
                .formLogin(form -> form.disable());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
        UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByUsername(username)
            .map(user -> User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .build())
            .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(username));
    }
}