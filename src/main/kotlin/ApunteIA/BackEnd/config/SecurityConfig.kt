package ApunteIA.BackEnd.config

import ApunteIA.BackEnd.entity.Role
import ApunteIA.BackEnd.security.JwtAuthenticationFilter
import ApunteIA.BackEnd.security.RestAccessDeniedHandler
import ApunteIA.BackEnd.security.RestAuthenticationEntryPoint
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

/**
 * Seguridad de la API con JWT (módulo "Security with JWT").
 *
 *  Públicas:      POST /auth/login, POST /auth/register, /h2-console (solo desarrollo), /error
 *  Solo ADMIN:    PATCH /users/{id}/status, GET /security-logs y GET /security-logs/{id} *  Resto:         requiere `Authorization: Bearer <token>` de cualquier usuario ACTIVE
 *
 * Es stateless: no hay sesión ni cookies, así que CSRF no aplica y se desactiva.
 * El PasswordEncoder (BCrypt) sigue en PasswordConfig.
*/
@Configuration
@EnableWebSecurity
class SecurityConfig(
private val jwtAuthenticationFilter: JwtAuthenticationFilter,
private val authenticationEntryPoint: RestAuthenticationEntryPoint,
private val accessDeniedHandler: RestAccessDeniedHandler,
) {

@Bean
fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
val path = PathPatternRequestMatcher.withDefaults()
http
.csrf { it.disable() }
.httpBasic { it.disable() }
.formLogin { it.disable() }
.logout { it.disable() }
.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
.headers { headers -> headers.frameOptions { it.sameOrigin() } } // la consola H2 usa frames
.authorizeHttpRequests { auth ->
auth
.requestMatchers(path.matcher(HttpMethod.POST, "/auth/login")).permitAll()
.requestMatchers(path.matcher(HttpMethod.POST, "/auth/register")).permitAll()
.requestMatchers(path.matcher("/h2-console/**")).permitAll()
.requestMatchers(path.matcher("/error")).permitAll()
.requestMatchers(path.matcher(HttpMethod.PATCH, "/users/*/status")).hasRole(Role.ADMIN)
.requestMatchers(path.matcher("/security-logs/**")).hasRole(Role.ADMIN)
.anyRequest().authenticated()
}
.exceptionHandling {
it.authenticationEntryPoint(authenticationEntryPoint)
it.accessDeniedHandler(accessDeniedHandler)
}
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
return http.build()
}

/** El filtro es un @Component: sin esto Spring Boot también lo registraría fuera de Spring Security. */
@Bean
fun jwtFilterRegistration(filter: JwtAuthenticationFilter): FilterRegistrationBean<JwtAuthenticationFilter> =
FilterRegistrationBean(filter).apply { isEnabled = false }
}