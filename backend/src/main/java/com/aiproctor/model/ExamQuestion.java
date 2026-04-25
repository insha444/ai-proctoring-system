package com.aiproctor.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "exam_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "option_a", nullable = false, length = 500)
    private String optionA;

    @Column(name = "option_b", nullable = false, length = 500)
    private String optionB;

    @Column(name = "option_c", nullable = false, length = 500)
    private String optionC;

    @Column(name = "option_d", nullable = false, length = 500)
    private String optionD;

    /**
     * Correct answer: A | B | C | D
     * NEVER exposed to students in API responses.
     */
    @Column(name = "correct_answer", nullable = false, length = 1)
    private String correctAnswer;

    @Column(name = "marks", nullable = false)
    @Builder.Default
    private Integer marks = 1;

    @Column(name = "question_order", nullable = false)
    @Builder.Default
    private Integer questionOrder = 0;
}
