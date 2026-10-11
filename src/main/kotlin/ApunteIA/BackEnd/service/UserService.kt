package ApunteIA.BackEnd.service

import ApunteIA.BackEnd.dto.user.UserResponse
import ApunteIA.BackEnd.entity.UserStatus

interface UserService {
    fun findAll(status: UserStatus? = null): List<UserResponse>
    fun findById(id: Long): UserResponse
    fun updateStatus(id: Long, newStatus: UserStatus): UserResponse
}