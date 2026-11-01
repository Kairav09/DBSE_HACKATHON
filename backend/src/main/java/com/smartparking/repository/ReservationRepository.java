package com.smartparking.repository;

import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Overlap check: an existing reservation overlaps [start,end) iff existing.start < end AND existing.end > start. */
    @Query("""
            SELECT COUNT(r) > 0 FROM Reservation r
            WHERE r.slot.id = :slotId
              AND r.status IN :statuses
              AND r.startTime < :end
              AND r.endTime > :start
            """)
    boolean existsOverlapping(@Param("slotId") Long slotId,
                              @Param("statuses") Collection<ReservationStatus> statuses,
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);

    @Query("""
            SELECT r FROM Reservation r
            WHERE r.slot.id = :slotId AND r.status IN :statuses
              AND r.startTime < :end AND r.endTime > :start
            """)
    List<Reservation> findOverlapping(@Param("slotId") Long slotId,
                                      @Param("statuses") Collection<ReservationStatus> statuses,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    /** Same overlap predicate, ignoring one reservation (used by reschedule). */
    @Query("""
            SELECT COUNT(r) > 0 FROM Reservation r
            WHERE r.slot.id = :slotId
              AND r.status IN :statuses
              AND r.id <> :excludeId
              AND r.startTime < :end
              AND r.endTime > :start
            """)
    boolean existsOverlappingExcluding(@Param("slotId") Long slotId,
                                       @Param("statuses") Collection<ReservationStatus> statuses,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end,
                                       @Param("excludeId") Long excludeId);

    /** Scheduler sweep: RESERVED bookings whose start is past the no-show grace and were never entered. */
    List<Reservation> findByStatusAndStartTimeBeforeAndEntryTimeIsNull(ReservationStatus status, LocalDateTime cutoff);

    boolean existsBySlotIdAndStatus(Long slotId, ReservationStatus status);

    @Query("""
            SELECT COUNT(r) > 0 FROM Reservation r
            WHERE r.slot.id = :slotId AND r.status = :status AND r.endTime >= :now
            """)
    boolean existsBySlotIdAndStatusAndEndTimeAfter(@Param("slotId") Long slotId,
                                                   @Param("status") ReservationStatus status,
                                                   @Param("now") LocalDateTime now);

    boolean existsBySlotId(Long slotId);
    boolean existsByVehicleId(Long vehicleId);

    @Query("SELECT r FROM Reservation r WHERE r.slot.location.id = :locationId")
    List<Reservation> findByLocationId(@Param("locationId") Long locationId);

    @Query("SELECT COUNT(r) > 0 FROM Reservation r WHERE r.slot.location.id = :locationId")
    boolean existsByLocationId(@Param("locationId") Long locationId);

    List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Reservation> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, ReservationStatus status);

    @Query("""
            SELECT r FROM Reservation r
            WHERE (:locationId IS NULL OR r.slot.location.id = :locationId)
              AND (:status IS NULL OR r.status = :status)
              AND (:dayStart IS NULL OR r.startTime >= :dayStart)
              AND (:dayEnd IS NULL OR r.startTime < :dayEnd)
            ORDER BY r.createdAt DESC
            """)
    List<Reservation> findForAdmin(@Param("locationId") Long locationId,
                                   @Param("status") ReservationStatus status,
                                   @Param("dayStart") LocalDateTime dayStart,
                                   @Param("dayEnd") LocalDateTime dayEnd);

    @Query("""
            SELECT r FROM Reservation r
            WHERE r.createdAt >= :dayStart AND r.createdAt < :dayEnd
            """)
    List<Reservation> findCreatedBetween(@Param("dayStart") LocalDateTime dayStart,
                                         @Param("dayEnd") LocalDateTime dayEnd);

    @Query("SELECT r FROM Reservation r WHERE r.slot.location.id = :locationId AND r.status <> :excluded")
    List<Reservation> findByLocationIdAndStatusNot(@Param("locationId") Long locationId,
                                                   @Param("excluded") ReservationStatus excluded);
}
