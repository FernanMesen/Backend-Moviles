package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.Enrollment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EnrollmentRepository : JpaRepository<Enrollment, Long> {
    fun findAllByUser_Id(userId: Long): List<Enrollment>
    fun findAllByCourse_Id(courseId: Long): List<Enrollment>
    fun existsByUser_IdAndCourse_Id(userId: Long, courseId: Long): Boolean
}
