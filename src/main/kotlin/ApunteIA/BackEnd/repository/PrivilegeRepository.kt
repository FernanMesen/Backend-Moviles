package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.Privilege
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PrivilegeRepository : JpaRepository<Privilege, Long> {
    fun findByName(name: String): Privilege?
    fun existsByName(name: String): Boolean
}
