package com.smartparking.repository;

import com.smartparking.model.WaitlistEntry;
import com.smartparking.model.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {
    List<WaitlistEntry> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<WaitlistEntry> findAllByOrderByCreatedAtDesc();
    /** FIFO queue for one location — earliest WAITING entry first. */
    List<WaitlistEntry> findByLocationIdAndStatusOrderByCreatedAtAsc(Long locationId, WaitlistStatus status);
    List<WaitlistEntry> findByStatusAndOfferedAtBefore(WaitlistStatus status, LocalDateTime cutoff);
}
