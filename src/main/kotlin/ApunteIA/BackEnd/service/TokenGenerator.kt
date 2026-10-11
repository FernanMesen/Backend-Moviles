package ApunteIA.BackEnd.service

import org.springframework.stereotype.Component
import java.util.UUID

/** Token opaco por ahora; en "Security with JWT" solo se cambia esta clase. */
@Component
class TokenGenerator {
    fun generate(): String = "apunteia-" + UUID.randomUUID().toString()
}