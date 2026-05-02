package com.therapea.backend.service;

import com.therapea.backend.dto.PaymentRequestDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentService {

    // Pulls from your application.properties / .env
    @Value("${paymongo.secret.key}")
    private String secretKey;

    private final String PAYMONGO_URL = "https://api.paymongo.com/v1/links";

    public String createPaymentLink(PaymentRequestDTO request) {
        RestTemplate restTemplate = new RestTemplate();

        // PayMongo uses Basic Auth: Encode the secret key with a colon at the end
        String authHeader = "Basic " + Base64.getEncoder().encodeToString((secretKey + ":").getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", authHeader);

        // PayMongo requires amounts in whole centavos (e.g., 1500 PHP = 150000)
        int amountInCents = (int) (Math.round(request.getAmount() * 100));

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("amount", amountInCents);
        attributes.put("description", request.getDescription());
        attributes.put("remarks", request.getEmail());
        // Redirect here after successful payment
        attributes.put("success_url", "http://localhost:5173/checkout?status=success");

        Map<String, Object> data = new HashMap<>();
        data.put("attributes", attributes);

        Map<String, Object> payload = new HashMap<>();
        payload.put("data", data);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(PAYMONGO_URL, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();

            if (responseBody != null && responseBody.containsKey("data")) {
                Map<String, Object> responseData = (Map<String, Object>) responseBody.get("data");
                Map<String, Object> responseAttributes = (Map<String, Object>) responseData.get("attributes");
                return (String) responseAttributes.get("checkout_url");
            }
            throw new RuntimeException("Failed to extract PayMongo checkout URL.");
        } catch (Exception e) {
            System.out.println("PayMongo Error: " + e.getMessage());
            throw new RuntimeException("Payment gateway connection error.");
        }
    }
}