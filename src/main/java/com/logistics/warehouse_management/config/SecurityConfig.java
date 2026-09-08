package com.logistics.warehouse_management.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/import/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/items/**", "/api/warehouses/**", "/api/storage-locations/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/items/**", "/api/warehouses/**", "/api/storage-locations/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/items/**", "/api/warehouses/**", "/api/storage-locations/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/items/**", "/api/warehouses/**", "/api/storage-locations/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/projects/**", "/api/pick-orders/**", "/api/delivery-notes/**").hasAnyRole("ADMIN", "MANAGER", "WAREHOUSE_WORKER")
                        .requestMatchers(HttpMethod.POST, "/api/goods-receipts/**", "/api/pick-orders/*/start", "/api/pick-orders/*/lines/*/scan", "/api/pick-orders/*/complete", "/api/delivery-notes/**").hasAnyRole("ADMIN", "WAREHOUSE_WORKER")
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
    UserDetailsService userDetailsService(
            @Value("${app.security.admin-username}") String adminUsername,
            @Value("${app.security.admin-password}") String adminPassword,
            @Value("${app.security.worker-username}") String workerUsername,
            @Value("${app.security.worker-password}") String workerPassword,
            @Value("${app.security.manager-username}") String managerUsername,
            @Value("${app.security.manager-password}") String managerPassword,
            PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername(adminUsername).password(encoder.encode(adminPassword)).roles("ADMIN").build(),
                User.withUsername(workerUsername).password(encoder.encode(workerPassword)).roles("WAREHOUSE_WORKER").build(),
                User.withUsername(managerUsername).password(encoder.encode(managerPassword)).roles("MANAGER").build());
    }
}