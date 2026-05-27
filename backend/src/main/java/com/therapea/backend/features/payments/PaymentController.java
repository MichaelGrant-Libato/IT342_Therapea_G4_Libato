package com.therapea.backend.features.payments;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/create-link")
    public ResponseEntity<?> createLink(@RequestBody PaymentRequestDTO request) {
        try {
            System.out.println("Generating Payment Link for: " + request.getEmail());
            System.out.println("Payment source: " + request.getSource());
            System.out.println("Return base URL: " + request.getReturnBaseUrl());

            String checkoutUrl = paymentService.createPaymentLink(request);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "checkoutUrl", checkoutUrl,
                    "message", "Payment link generated successfully."
            ));

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Payment Gateway Error: " + e.getMessage()
            ));
        }
    }
}