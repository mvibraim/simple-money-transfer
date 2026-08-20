package com.example.simple_money_transfers.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.security")
@Validated
public class AppProperties {

  @NotEmpty private List<@Valid ApiKeyEntry> apiKeys = new ArrayList<>();

  public List<ApiKeyEntry> getApiKeys() {
    return apiKeys;
  }

  public void setApiKeys(List<ApiKeyEntry> apiKeys) {
    this.apiKeys = apiKeys;
  }
}
