package com.smartparking.service;

import com.smartparking.model.NotificationType;
import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import com.smartparking.model.WaitlistEntry;
import com.smartparking.model.WaitlistStatus;
import com.smartparking.repository.ReservationRepository;
import com.smartparking.repository.WaitlistEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * v3 scheduler — 60-second sweep (contract):
 *  1. RESERVED reservations whose start is >30 min past and were never entered
 *     -> NO_SHOW (+ notification). Waitlist promotion is SKIPPED for no-shows:
 *     the freed window has already passed, so an offer would be useless.
 *  2. OFFERED waitlist entries not claimed within 15 minutes -> EXPIRED,
 *     then the next WAITING entry for that slot/location is promoted.
 */
@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);
    static final long NO_SHOW_GRACE_MINUTES = 30;
    static final long OFFER_EXPIRY_MINUTES = 15;

    private final ReservationRepository reservationRepository;
    private final WaitlistEntryRepository waitlistRepository;
    private final NotificationService notificationService;
    private final WaitlistService waitlistService;

    public ScheduledTasks(ReservationRepository reservationRepository,
                          WaitlistEntryRepository waitlistRepository,
                          NotificationService notificationService,
                          WaitlistService waitlistService) {
        this.reservationRepository = reservationRepository;
        this.waitlistRepository = waitlistRepository;
        this.notificationService = notificationService;
        this.waitlistService = waitlistService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Transactional
    public void sweep() {
        markNoShows();
        expireOffers();
    }

    void markNoShows() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(NO_SHOW_GRACE_MINUTES);
        List<Reservation> stale = reservationRepository
                .findByStatusAndStartTimeBeforeAndEntryTimeIsNull(ReservationStatus.RESERVED, cutoff);
        for (Reservation r : stale) {
            r.setStatus(ReservationStatus.NO_SHOW);
            reservationRepository.save(r);
            notificationService.create(r.getUser(), NotificationType.RESERVATION_NO_SHOW,
                    "Reservation marked as no-show",
                    "Reservation #" + r.getId() + " for slot " + r.getSlot().getSlotCode() + " at "
                            + r.getSlot().getLocation().getName()
                            + " was marked NO_SHOW because no entry was recorded within 30 minutes of the start time.",
                    r.getId());
            log.info("Reservation {} marked NO_SHOW (start {} )", r.getId(), r.getStartTime());
        }
    }

    void expireOffers() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(OFFER_EXPIRY_MINUTES);
        List<WaitlistEntry> expired = waitlistRepository
                .findByStatusAndOfferedAtBefore(WaitlistStatus.OFFERED, cutoff);
        for (WaitlistEntry e : expired) {
            e.setStatus(WaitlistStatus.EXPIRED);
            waitlistRepository.save(e);
            log.info("Waitlist entry {} OFFERED -> EXPIRED (slot {})", e.getId(),
                    e.getOfferedSlot() == null ? "-" : e.getOfferedSlot().getSlotCode());
            if (e.getOfferedSlot() != null) {
                // Offer the same slot/window on to the next WAITING entry.
                waitlistService.promoteForSlot(e.getOfferedSlot(), e.getStartTime(), e.getEndTime());
            }
        }
    }
}
