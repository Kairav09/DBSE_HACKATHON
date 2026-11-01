package com.smartparking.service;

import com.smartparking.dto.NotificationDtos;
import com.smartparking.exception.ApiException;
import com.smartparking.model.Notification;
import com.smartparking.model.NotificationType;
import com.smartparking.model.User;
import com.smartparking.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** In-app notifications (v3). Created by booking/payment/waitlist/scheduler flows. */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;

    public NotificationService(NotificationRepository notificationRepository,
                               CurrentUserService currentUserService) {
        this.notificationRepository = notificationRepository;
        this.currentUserService = currentUserService;
    }

    public static NotificationDtos.NotificationResponse toResponse(Notification n) {
        return new NotificationDtos.NotificationResponse(
                n.getId(), n.getType().name(), n.getTitle(), n.getMessage(),
                n.getRelatedId(), Boolean.TRUE.equals(n.getIsRead()), n.getCreatedAt());
    }

    /** Internal hook used by other services — never fails the caller's flow. */
    @Transactional
    public Notification create(User user, NotificationType type, String title, String message, Long relatedId) {
        return notificationRepository.save(new Notification(user, type, title, message, relatedId));
    }

    @Transactional(readOnly = true)
    public List<NotificationDtos.NotificationResponse> myNotifications() {
        User current = currentUserService.getCurrentUser();
        return notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(current.getId())
                .stream().map(NotificationService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        User current = currentUserService.getCurrentUser();
        return notificationRepository.countByUserIdAndIsReadFalse(current.getId());
    }

    @Transactional
    public NotificationDtos.NotificationResponse markRead(Long id) {
        User current = currentUserService.getCurrentUser();
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Notification not found: " + id));
        if (!n.getUser().getId().equals(current.getId())) {
            throw ApiException.forbidden("Notification does not belong to the current user");
        }
        n.setIsRead(true);
        return toResponse(notificationRepository.save(n));
    }

    @Transactional
    public long markAllRead() {
        User current = currentUserService.getCurrentUser();
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalse(current.getId());
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
        return 0L;
    }
}
