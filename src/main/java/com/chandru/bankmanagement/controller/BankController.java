package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.BankResponse;
import com.chandru.bankmanagement.dto.CustomerRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class BankController {

    @GetMapping("/bank")
    public BankResponse getBank() {

        return new BankResponse(
                "State Bank",
                "Coimbatore",
                "Tamil Nadu"
        );
    }

    @PostMapping("/customer")
    public String createCustomer(@RequestBody CustomerRequest customer) {

        return "Customer " + customer.getName() + " registered successfully";
    }
}