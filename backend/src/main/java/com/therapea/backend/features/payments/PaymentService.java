package com.therapea.backend.features.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class PaymentService {

    @Value("${paymongo.secret.key}")
    private String secretKey;

    @Value("${frontend.url:http://localhost}")
    private String frontendUrl;

    private static final String PAYMONGO_URL =
            "https://api.paymongo.com/v1/checkout_sessions";

    public String createPaymentLink(PaymentRequestDTO request) {
        validateRequest(request);

        RestTemplate restTemplate = new RestTemplate();

        String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", authHeader);

        int amountInCents = (int) Math.round(request.getAmount() * 100);

        boolean isMobile = "mobile".equalsIgnoreCase(request.getSource());

        String successUrl;
        String cancelUrl;

        if (isMobile) {
            successUrl = "therapea://checkout/success";
            cancelUrl = "therapea://checkout/failed";
        } else {
            String webBaseUrl = resolveWebReturnBaseUrl(request.getReturnBaseUrl());

            successUrl = webBaseUrl + "/checkout?status=success";
            cancelUrl = webBaseUrl + "/checkout?status=failed";
        }

        System.out.println("PayMongo success_url: " + successUrl);
        System.out.println("PayMongo cancel_url: " + cancelUrl);

        Map<String, Object> lineItem = new HashMap<>();
        lineItem.put("currency", "PHP");
        lineItem.put("amount", amountInCents);
        lineItem.put("name", request.getDescription());
        lineItem.put("quantity", 1);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("line_items", Collections.singletonList(lineItem));
        attributes.put("payment_method_types", Arrays.asList("gcash", "paymaya", "card", "qrph"));
        attributes.put("success_url", successUrl);
        attributes.put("cancel_url", cancelUrl);
        attributes.put("send_email_receipt", false);
        attributes.put("show_description", true);
        attributes.put("show_line_items", true);

        Map<String, Object> data = new HashMap<>();
        data.put("attributes", attributes);

        Map<String, Object> payload = new HashMap<>();
        payload.put("data", data);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    PAYMONGO_URL,
                    entity,
                    Map.class
            );

            Map<String, Object> responseBody = response.getBody();

            if (responseBody == null || !responseBody.containsKey("data")) {
                throw new RuntimeException("PayMongo response has no data.");
            }

            Map<String, Object> responseData =
                    (Map<String, Object>) responseBody.get("data");

            Map<String, Object> responseAttrs =
                    (Map<String, Object>) responseData.get("attributes");

            String checkoutUrl = (String) responseAttrs.get("checkout_url");

            if (checkoutUrl == null || checkoutUrl.isBlank()) {
                throw new RuntimeException("PayMongo response has no checkout_url.");
            }

            return checkoutUrl;

        } catch (Exception e) {
            System.out.println("PayMongo Error: " + e.getMessage());
            throw new RuntimeException("Payment gateway connection error.");
        }
    }

    private void validateRequest(PaymentRequestDTO request) {
        if (request == null) {
            throw new RuntimeException("Payment request is required.");
        }

        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new RuntimeException("Valid payment amount is required.");
        }

        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new RuntimeException("Payment description is required.");
        }

        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new RuntimeException("Customer email is required.");
        }
    }

    private String resolveWebReturnBaseUrl(String returnBaseUrl) {
        String baseUrl = returnBaseUrl;

        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = frontendUrl;
        }

        baseUrl = baseUrl.trim();

        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        if (!isAllowedWebReturnUrl(baseUrl)) {
            System.out.println("Blocked unsafe returnBaseUrl: " + baseUrl);
            baseUrl = frontendUrl;
        }

        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl;
    }

    private boolean isAllowedWebReturnUrl(String url) {
        return url.startsWith("http://localhost")
                || url.startsWith("http://127.0.0.1")
                || url.startsWith("http://192.168.")
                || url.startsWith("https://")
                || url.startsWith("http://localhost:5173");
    }
}