package ApunteIA.BackEnd.service

import ApunteIA.BackEnd.dto.auth.LoginRequest
import ApunteIA.BackEnd.dto.auth.LoginResponse
import ApunteIA.BackEnd.dto.auth.RegisterUserRequest
import ApunteIA.BackEnd.dto.auth.ChangePasswordRequest
import ApunteIA.BackEnd.dto.user.UserResponse

interface AuthService {
    fun login(request: LoginRequest): LoginResponse
    fun register(request: RegisterUserRequest): UserResponse
    fun changePassword(userId: Long, request: ChangePasswordRequest)
}
