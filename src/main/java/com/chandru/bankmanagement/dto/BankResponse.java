package com.chandru.bankmanagement.dto;

public class BankResponse {

    private String bankName;
    private String branch;
    private String location;

    public BankResponse() {
    }

    public BankResponse(String bankName, String branch, String location) {
        this.bankName = bankName;
        this.branch = branch;
        this.location = location;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}