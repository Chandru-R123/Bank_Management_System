package com.chandru.bankmanagement.dto;

public class CustomerResponse {

    private Long customerId;
    private String name;
    private String email;
    private String phone;
    private String address;
    /** True when the customer has a linked Keycloak login (can use online banking). */
    private boolean onlineBanking;

    public CustomerResponse() {
    }

    public CustomerResponse(Long customerId, String name, String email, String phone, String address) {
        this.customerId = customerId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.address = address;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isOnlineBanking() {
        return onlineBanking;
    }

    public void setOnlineBanking(boolean onlineBanking) {
        this.onlineBanking = onlineBanking;
    }
}
