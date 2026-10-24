package ApunteIA.BackEnd.seed

import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Carga los datos de prueba al arrancar la app (excepto en los perfiles "prod" y "render").
 * Cada persona agrega su propio runner con un @Order mayor:
 *   1 Identidad (A) → 2 Académico/Auditoría (B) → 3 Notas (C) → 4 Evaluaciones (D)
 */
@Component
@Profile("!prod & !render")
@Order(1)
class IdentityDataLoader(private val identitySeeder: IdentitySeeder) : CommandLineRunner {
    override fun run(vararg args: String) {
        identitySeeder.seed()
    }
}
