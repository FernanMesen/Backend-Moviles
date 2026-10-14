package ApunteIA.BackEnd.entity

/** assessments.type */
enum class AssessmentType { QUIZ, EXAM, PRACTICE }

/** assessments.difficulty */
enum class AssessmentDifficulty { EASY, MEDIUM, HARD }

/** Estado de un intento, derivado de assessment_attempts.completed_at */
enum class AttemptStatus { IN_PROGRESS, COMPLETED }