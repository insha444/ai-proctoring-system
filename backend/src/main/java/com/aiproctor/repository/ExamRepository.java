package com.aiproctor.repository;

import com.aiproctor.model.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {

    /** All active exams ordered by start time */
    List<Exam> findByIsActiveTrueOrderByStartTimeAsc();

    /** Available exams: active AND not yet ended */
    @Query("""
        SELECT e FROM Exam e
        WHERE e.isActive = true
          AND e.endTime > :now
        ORDER BY e.startTime ASC
        """)
    List<Exam> findAvailableExams(@Param("now") LocalDateTime now);

    /** Fetch exam with questions eagerly (avoids N+1 in exam detail) */
    @Query("""
        SELECT DISTINCT e FROM Exam e
        LEFT JOIN FETCH e.questions q
        WHERE e.id = :id
        ORDER BY q.questionOrder ASC
        """)
    Optional<Exam> findByIdWithQuestions(@Param("id") Long id);

    /** All exams created by a specific admin */
    List<Exam> findByCreatedByIdOrderByCreatedAtDesc(Long adminId);
}
