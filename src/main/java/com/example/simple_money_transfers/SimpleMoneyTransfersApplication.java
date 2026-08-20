package com.example.simple_money_transfers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SimpleMoneyTransfersApplication {

	public static void main(String[] args) {
		SpringApplication.run(SimpleMoneyTransfersApplication.class, args);
	}

}
