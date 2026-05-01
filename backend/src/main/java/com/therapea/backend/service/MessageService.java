package com.therapea.backend.service;

import com.therapea.backend.entity.MessageEntity;
import com.therapea.backend.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    public MessageEntity saveMessage(MessageEntity message) {
        if (message.getTimestamp() == null) {
            message.setTimestamp(LocalDateTime.now());
        }
        return messageRepository.save(message);
    }

    public List<MessageEntity> getConversation(String user1, String user2) {
        return messageRepository.findConversation(user1, user2);
    }
}