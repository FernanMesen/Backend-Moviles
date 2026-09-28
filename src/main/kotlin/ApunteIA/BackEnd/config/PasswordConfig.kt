package ApunteIA.BackEnd.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * Encoder de contraseñas usado para llenar users.password_hash.
 * Cuando llegue el módulo "Security with JWT" se reutiliza este mismo bean.
 */
@Configuration
class PasswordConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}