package com.chandru.bankmanagement.dto;

public class RegisterRequest {

    private String username;
    private String password;
    private String role;
    private Long customerId;

    public RegisterRequest() {
    }

    public RegisterRequest(String username,
                           String password,
                           String role,
                           Long customerId) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.customerId = customerId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }
}