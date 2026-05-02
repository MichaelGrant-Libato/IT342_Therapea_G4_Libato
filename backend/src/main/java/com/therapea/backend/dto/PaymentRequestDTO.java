package com.therapea.backend.dto;

public class PaymentRequestDTO {
    private Double amount;
    private String description;
    private String email;

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}