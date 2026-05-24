package com.therapea.backend.features.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class PaymentService {

    @Value("${paymongo.secret.key}")
    private String secretKey;

    private final String PAYMONGO_URL = "https://api.paymongo.com/v1/checkout_sessions";

    public String createPaymentLink(PaymentRequestDTO request) {
        RestTemplate restTemplate = new RestTemplate();

        String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", authHeader);

        int amountInCents = (int) (Math.round(request.getAmount() * 100));

        boolean isMobile = "mobile".equalsIgnoreCase(request.getSource());

        String successUrl = isMobile
                ? "therapea://checkout/success"
                : "http://localhost:5173/checkout?status=success";

        String cancelUrl = isMobile
                ? "therapea://checkout/failed"
                : "http://localhost:5173/checkout?status=failed";


        Map<String, Object> lineItem = new HashMap<>();
        lineItem.put("currency", "PHP");
        lineItem.put("amount",   amountInCents);
        lineItem.put("name",     request.getDescription());
        lineItem.put("quantity", 1);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("line_items",           Collections.singletonList(lineItem));
        attributes.put("payment_method_types", Arrays.asList("gcash", "paymaya", "card", "qrph"));
        attributes.put("success_url",          successUrl);
        attributes.put("cancel_url",           cancelUrl);

        Map<String, Object> data    = new HashMap<>();
        data.put("attributes", attributes);

        Map<String, Object> payload = new HashMap<>();
        payload.put("data", data);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    PAYMONGO_URL, entity, Map.class);

            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("data")) {
                Map<String, Object> responseData  = (Map<String, Object>) responseBody.get("data");
                Map<String, Object> responseAttrs = (Map<String, Object>) responseData.get("attributes");
                return (String) responseAttrs.get("checkout_url");
            }
            throw new RuntimeException("Failed to extract checkout URL.");
        } catch (Exception e) {
            System.out.println("PayMongo Error: " + e.getMessage());
            throw new RuntimeException("Payment gateway connection error.");
        }
    }
}