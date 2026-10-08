package com.chandru.bankmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BankManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(BankManagementApplication.class, args);
	}

}
