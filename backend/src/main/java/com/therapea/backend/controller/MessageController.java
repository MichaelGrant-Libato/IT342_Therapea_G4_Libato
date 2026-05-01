package com.therapea.backend.controller;

import com.therapea.backend.entity.MessageEntity;
import com.therapea.backend.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "*")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestBody MessageEntity messageEntity) {
        try {
            MessageEntity savedMessage = messageService.saveMessage(messageEntity);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", savedMessage);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getConversation(@RequestParam String user1, @RequestParam String user2) {
        try {
            List<MessageEntity> messages = messageService.getConversation(user1, user2);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("messages", messages);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}