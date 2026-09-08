package com.logistics.warehouse_management;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SecurityConfigTests {

    @Autowired
    private UserDetailsService userDetailsService;

    @Test
    void configuresDemoUsersWithRolesAndEncodedPasswords() {
        var admin = userDetailsService.loadUserByUsername("admin");
        var worker = userDetailsService.loadUserByUsername("worker");
        var manager = userDetailsService.loadUserByUsername("manager");

        assertTrue(admin.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        assertTrue(worker.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_WAREHOUSE_WORKER")));
        assertTrue(manager.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER")));
        assertTrue(!admin.getPassword().equals("change-me-admin"));
    }
}
