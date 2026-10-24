package ApunteIA.BackEnd.seed

import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Se ejecuta después de identidad y académico/auditoría, antes de evaluaciones. */
@Component
@Profile("!prod & !render")
@Order(3)
class NoteDataLoader(private val noteSeeder: NoteSeeder) : CommandLineRunner {
    override fun run(vararg args: String) {
        noteSeeder.seed()
    }
}
