package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.Assessment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

@Repository
interface AssessmentRepository : JpaRepository<Assessment, Long> {
    fun findByTitle(title: String): Assessment?
    fun existsByTitle(title: String): Boolean
    fun findAllByCourse_Id(courseId: Long): List<Assessment>

    @Modifying
    @Query("update Assessment a set a.createdBy = null where a.createdBy.id = :userId")
    fun clearCreator(@Param("userId") userId: Long): Int
}
