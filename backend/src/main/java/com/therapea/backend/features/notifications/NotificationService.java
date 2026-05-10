package com.therapea.backend.features.notifications;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository repository;

    public void createNotification(String email, String title, String message, String type) {
        NotificationEntity notif = new NotificationEntity();
        notif.setUserEmail(email);
        notif.setTitle(title);
        notif.setMessage(message);
        notif.setType(type);
        repository.save(notif);
    }
}