package ApunteIA.BackEnd.service.impl

import ApunteIA.BackEnd.dto.user.UserResponse
import ApunteIA.BackEnd.entity.UserStatus
import ApunteIA.BackEnd.exception.ConflictException
import ApunteIA.BackEnd.exception.NotFoundException
import ApunteIA.BackEnd.mapper.UserMapper
import ApunteIA.BackEnd.repository.UserRepository
import ApunteIA.BackEnd.service.UserService
import ApunteIA.BackEnd.service.SecurityLogService
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Reglas: 404 si no existe · DELETED es final (409) · mismo estado = 200 sin cambios · nunca se borra la fila. */
@Service
@Transactional(readOnly = true)
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val userMapper: UserMapper,
    private val securityLogService: SecurityLogService,
) : UserService {

    override fun findAll(status: UserStatus?): List<UserResponse> {
        val users = if (status == null) {
            userRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
        } else {
            userRepository.findAllByStatus(status).sortedBy { it.id }
        }
        return userMapper.toResponseList(users)
    }

    override fun findById(id: Long): UserResponse = userMapper.toResponse(findUserOrThrow(id))

    @Transactional
    override fun updateStatus(id: Long, newStatus: UserStatus): UserResponse {
        val user = findUserOrThrow(id)

        if (user.status == newStatus) return userMapper.toResponse(user)

        if (user.status == UserStatus.DELETED) {
            throw ConflictException("El usuario $id está eliminado y su estado ya no se puede cambiar")
        }

        val previousStatus = user.status
        user.status = newStatus
        val saved = userRepository.saveAndFlush(user)
        securityLogService.record(SecurityLogService.USER_STATUS_CHANGED, saved.id,
            "Estado cambiado de $previousStatus a $newStatus")
        return userMapper.toResponse(saved)
    }

    private fun findUserOrThrow(id: Long) =
        userRepository.findById(id).orElseThrow { NotFoundException("No existe un usuario con id $id") }
}
