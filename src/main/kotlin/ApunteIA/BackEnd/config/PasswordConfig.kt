package ApunteIA.BackEnd.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * Encoder de contraseñas usado para llenar users.password_hash.
 * Lo usan el login (AuthServiceImpl) y los seeders; la configuración de JWT está en SecurityConfig.
 */
@Configuration
class PasswordConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}