package com.vori.backend.attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
public interface AttendanceCheckinRepository extends JpaRepository<AttendanceCheckin, Long> {
    Optional<AttendanceCheckin> findByUserIdAndCheckedInDate(Long userId, LocalDate date);
    List<AttendanceCheckin> findByUserIdAndCheckedInDateBetweenOrderByCheckedInDate(Long userId, LocalDate from, LocalDate to);
}
