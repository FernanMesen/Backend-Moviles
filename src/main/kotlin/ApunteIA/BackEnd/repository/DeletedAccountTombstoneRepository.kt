package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.DeletedAccountTombstone
import org.springframework.data.jpa.repository.JpaRepository

interface DeletedAccountTombstoneRepository : JpaRepository<DeletedAccountTombstone, Long> {
    fun existsByEmailHash(emailHash: String): Boolean
}
