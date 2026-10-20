package ApunteIA.BackEnd

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

// Sin usuario en memoria con contraseña generada: la autenticación la hace el JwtAuthenticationFilter
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@ConfigurationPropertiesScan // registra JwtProperties (app.jwt.*)
class BackendApplication

fun main(args: Array<String>) {
    runApplication<BackendApplication>(*args)
}