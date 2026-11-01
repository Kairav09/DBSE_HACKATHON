package com.smartparking.controller;

import com.smartparking.dto.NotificationDtos;
import com.smartparking.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationDtos.NotificationResponse>> list() {
        return ResponseEntity.ok(notificationService.myNotifications());
    }

    @GetMapping("/unread-count")
    public ResponseEntity<NotificationDtos.UnreadCountResponse> unreadCount() {
        return ResponseEntity.ok(new NotificationDtos.UnreadCountResponse(notificationService.unreadCount()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationDtos.NotificationResponse> read(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markRead(id));
    }

    @PostMapping("/read-all")
    public ResponseEntity<NotificationDtos.UnreadCountResponse> readAll() {
        return ResponseEntity.ok(new NotificationDtos.UnreadCountResponse(notificationService.markAllRead()));
    }
}
