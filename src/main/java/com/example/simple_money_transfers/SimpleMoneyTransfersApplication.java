package com.example.simple_money_transfers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// UserDetailsServiceAutoConfiguration excluded: this service is API-key
// only (F04's ApiKeyAuthFilter), never username/password, so Boot's
// default in-memory user - and its logged "generated security password" -
// is dead weight that only invites the question of what it's for.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class SimpleMoneyTransfersApplication {

  public static void main(String[] args) {
    SpringApplication.run(SimpleMoneyTransfersApplication.class, args);
  }
}
