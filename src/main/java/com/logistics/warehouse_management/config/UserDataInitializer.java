package com.logistics.warehouse_management.config;

import com.logistics.warehouse_management.model.User;
import com.logistics.warehouse_management.model.UserRole;
import com.logistics.warehouse_management.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String workerUsername;
    private final String workerPassword;
    private final String managerUsername;
    private final String managerPassword;

    public UserDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                               @Value("${app.security.admin-username}") String adminUsername,
                               @Value("${app.security.admin-password}") String adminPassword,
                               @Value("${app.security.worker-username}") String workerUsername,
                               @Value("${app.security.worker-password}") String workerPassword,
                               @Value("${app.security.manager-username}") String managerUsername,
                               @Value("${app.security.manager-password}") String managerPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.workerUsername = workerUsername;
        this.workerPassword = workerPassword;
        this.managerUsername = managerUsername;
        this.managerPassword = managerPassword;
    }

    @Override
    public void run(String... args) {
        createIfMissing(adminUsername, adminPassword, UserRole.ADMIN);
        createIfMissing(workerUsername, workerPassword, UserRole.WAREHOUSE_WORKER);
        createIfMissing(managerUsername, managerPassword, UserRole.MANAGER);
    }

    private void createIfMissing(String username, String password, UserRole role) {
        if (userRepository.findByUsername(username).isEmpty()) {
            userRepository.save(new User(null, username, passwordEncoder.encode(password), role));
        }
    }
}