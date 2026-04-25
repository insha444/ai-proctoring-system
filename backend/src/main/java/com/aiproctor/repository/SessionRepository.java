package com.aiproctor.repository;

import com.aiproctor.model.ExamSession;
import com.aiproctor.model.ExamSession.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<ExamSession, Long> {

    Optional<ExamSession> findByStudentIdAndExamId(Long studentId, Long examId);

    boolean existsByStudentIdAndExamId(Long studentId, Long examId);

    List<ExamSession> findByStudentIdOrderByStartedAtDesc(Long studentId);

    List<ExamSession> findByExamIdOrderByStartedAtDesc(Long examId);

    List<ExamSession> findByStatusOrderByStartedAtDesc(SessionStatus status);

    /** Fetch session with student + exam eagerly for admin views */
    @Query("""
        SELECT s FROM ExamSession s
        JOIN FETCH s.student
        JOIN FETCH s.exam
        WHERE s.id = :id
        """)
    Optional<ExamSession> findByIdWithDetails(@Param("id") Long id);

    /** All in-progress sessions — used by admin live monitor */
    @Query("""
        SELECT s FROM ExamSession s
        JOIN FETCH s.student
        JOIN FETCH s.exam
        WHERE s.status = 'IN_PROGRESS'
        ORDER BY s.startedAt ASC
        """)
    List<ExamSession> findAllInProgress();

    /** Sessions for an exam, ordered by score descending (results/leaderboard) */
    @Query("""
        SELECT s FROM ExamSession s
        JOIN FETCH s.student
        WHERE s.exam.id = :examId
          AND s.status = 'SUBMITTED'
        ORDER BY s.score DESC
        """)
    List<ExamSession> findSubmittedByExamIdOrderByScore(@Param("examId") Long examId);
}
