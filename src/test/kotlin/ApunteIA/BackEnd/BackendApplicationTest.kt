package ApunteIA.BackEnd

import ApunteIA.BackEnd.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertNotNull

/** Levanta la app completa (con el DataLoader) sobre una H2 en memoria. */
@SpringBootTest
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:apunteia-it;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"])
class BackendApplicationTest @Autowired constructor(
    private val userRepository: UserRepository,
) {
    @Test
    fun `el contexto arranca y la semilla de identidad se ejecuta`() {
        assertNotNull(userRepository.findByEmail("demo@apunteia.com"))
    }
}
