package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.Course
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CourseRepository : JpaRepository<Course, Long> {
    fun findByCode(code: String): Course?
    fun existsByCode(code: String): Boolean
    fun findByCodeIgnoreCase(code: String): Course?
    fun existsByCodeIgnoreCase(code: String): Boolean

    @Modifying
    @Query("update Course c set c.createdBy = null where c.createdBy.id = :userId")
    fun clearCreator(@Param("userId") userId: Long): Int
}
