package com.therapea.backend.service;

import com.therapea.backend.dto.PaymentRequestDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class PaymentService {

    @Value("${paymongo.secret.key}")
    private String secretKey;

    // 🔴 CRITICAL FIX: Changed endpoint to checkout_sessions
    private final String PAYMONGO_URL = "https://api.paymongo.com/v1/checkout_sessions";

    public String createPaymentLink(PaymentRequestDTO request) {
        RestTemplate restTemplate = new RestTemplate();

        String authHeader = "Basic " + Base64.getEncoder().encodeToString((secretKey + ":").getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", authHeader);

        int amountInCents = (int) (Math.round(request.getAmount() * 100));

        // 🔴 CRITICAL FIX: The payload structure for Checkout Sessions is different from Links.
        // It requires an array of "line_items".
        Map<String, Object> lineItem = new HashMap<>();
        lineItem.put("currency", "PHP");
        lineItem.put("amount", amountInCents);
        lineItem.put("name", request.getDescription());
        lineItem.put("quantity", 1);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("line_items", Collections.singletonList(lineItem));
        attributes.put("payment_method_types", Arrays.asList("gcash", "paymaya", "card", "qrph"));

        // This success_url will now be respected by PayMongo, triggering the auto-redirect!
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
            throw new RuntimeException("Failed to extract checkout URL.");
        } catch (Exception e) {
            System.out.println("PayMongo Error: " + e.getMessage());
            throw new RuntimeException("Payment gateway connection error.");
        }
    }
}