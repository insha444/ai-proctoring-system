package com.aiproctor.repository;

import com.aiproctor.model.Violation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ViolationRepository extends JpaRepository<Violation, Long> {

    List<Violation> findBySessionIdOrderByDetectedAtDesc(Long sessionId);

    List<Violation> findByStudentIdOrderByDetectedAtDesc(Long studentId);

    long countBySessionId(Long sessionId);

    long countBySessionIdAndViolationType(Long sessionId, String violationType);

    @Query("""
        SELECT v FROM Violation v
        JOIN FETCH v.student
        WHERE v.exam.id = :examId
        ORDER BY v.detectedAt DESC
        """)
    List<Violation> findByExamIdWithStudent(@Param("examId") Long examId);

    @Query("""
        SELECT v FROM Violation v
        JOIN FETCH v.student
        JOIN FETCH v.session
        WHERE v.session.id = :sessionId
        ORDER BY v.detectedAt DESC
        """)
    List<Violation> findBySessionIdWithDetails(@Param("sessionId") Long sessionId);
}
