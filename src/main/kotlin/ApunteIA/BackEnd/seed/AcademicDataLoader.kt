package ApunteIA.BackEnd.seed

import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Se ejecuta después de la carga de identidad (@Order(1)). */
@Component
@Profile("!prod & !render")
@Order(2)
class AcademicDataLoader(private val academicSeeder: AcademicSeeder) : CommandLineRunner {
    override fun run(vararg args: String) {
        academicSeeder.seed()
    }
}
