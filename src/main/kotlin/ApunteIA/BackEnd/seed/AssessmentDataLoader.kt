package ApunteIA.BackEnd.seed

import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Se ejecuta de último, después de identidad, académico/auditoría y notas. */
@Component
@Profile("!prod & !render")
@Order(4)
class AssessmentDataLoader(private val assessmentSeeder: AssessmentSeeder) : CommandLineRunner {
    override fun run(vararg args: String) {
        assessmentSeeder.seed()
    }
}
