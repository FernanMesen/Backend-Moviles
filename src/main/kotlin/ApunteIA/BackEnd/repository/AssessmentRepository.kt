package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.Assessment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AssessmentRepository : JpaRepository<Assessment, Long> {
    fun findByTitle(title: String): Assessment?
    fun existsByTitle(title: String): Boolean
    fun findAllByCourse_Id(courseId: Long): List<Assessment>
}