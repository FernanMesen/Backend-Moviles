package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.Course
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CourseRepository : JpaRepository<Course, Long> {
    fun findByCode(code: String): Course?
    fun existsByCode(code: String): Boolean
}
