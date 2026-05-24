package com.therapea.backend.features.payments;

public class PaymentRequestDTO {
    private Double amount;
    private String description;
    private String email;
    private String source; // "web" or "mobile"

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}