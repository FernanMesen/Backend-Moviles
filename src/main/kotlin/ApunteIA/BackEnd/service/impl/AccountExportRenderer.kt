package ApunteIA.BackEnd.service.impl

import ApunteIA.BackEnd.exception.BadRequestException
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream

@Component
class AccountExportRenderer(private val objectMapper: ObjectMapper) {
    fun render(data: Map<String, Any?>, requestedFormat: String): Triple<ByteArray, MediaType, String> =
        when (requestedFormat.trim().lowercase()) {
            "json" -> Triple(objectMapper.writeValueAsBytes(data), MediaType.APPLICATION_JSON, "json")
            "csv" -> Triple(csv(data).toByteArray(Charsets.UTF_8).let { byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + it }, MediaType.parseMediaType("text/csv;charset=UTF-8"), "csv")
            "pdf" -> Triple(pdf(data), MediaType.APPLICATION_PDF, "pdf")
            else -> throw BadRequestException("Formato no soportado: '$requestedFormat'. Valores permitidos: json, pdf, csv")
        }

    private fun csv(data: Map<String, Any?>): String = buildString {
        appendLine("Tipo;ID;Fecha;Título;Curso;Detalle;Resultado")
        val account = data["account"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
        appendCsvRow("cuenta", account["id"], account["createdAt"], account["fullName"], "", account["email"], "")

        (data["notes"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { note ->
            appendCsvRow("apunte", note["id"], note["createdAt"], note["title"], note["courseCode"], note["summary"] ?: note["content"], note["processingStatus"])
        }
        (data["enrollments"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { enrollment ->
            appendCsvRow("inscripcion", enrollment["id"], enrollment["enrolledAt"], enrollment["courseName"], enrollment["courseCode"], "", "")
        }
        (data["assessmentAttempts"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { attempt ->
            appendCsvRow("evaluacion", attempt["id"], attempt["completedAt"] ?: attempt["startedAt"], attempt["assessmentTitle"], "", "Duración: ${attempt["durationSeconds"]} s", attempt["score"])
        }
        (data["createdAssessments"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { assessment ->
            appendCsvRow("cuestionario", assessment["id"], "", assessment["title"], assessment["courseId"], assessment["description"], assessment["type"])
        }
        val activityDays = studyActivityDays(data)
        appendCsvRow("metrica_estudio", "", "", "Dias con actividad registrada", "", "Racha maxima: ${longestStreak(activityDays)} dias", activityDays.size)
        (data["securityEvents"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { event ->
            appendCsvRow("evento", event["id"], event["occurredAt"], event["eventType"], "", event["details"], "")
        }
    }

    private fun StringBuilder.appendCsvRow(vararg cells: Any?) {
        appendLine(cells.joinToString(";") { cell ->
            val text = cell?.toString().orEmpty()
            "\"${text.replace("\"", "\"\"")}\""
        })
    }

    private fun pdf(data: Map<String, Any?>): ByteArray {
        val lines = mutableListOf<String>()
        val account = data["account"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
        lines += "Respaldo de datos personales - ApunteIA"
        lines += "Generado: ${data["exportedAt"]}"
        lines += ""
        lines += "Cuenta"
        lines += "Nombre: ${account["fullName"]}"
        lines += "Correo: ${account["email"]}"
        lines += "Proveedor de acceso: ${account["authProvider"]}"

        lines += ""
        lines += "Apuntes y resumenes (${(data["notes"] as? List<*>)?.size ?: 0})"
        (data["notes"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { note ->
            lines += "- ${note["title"]} | Curso: ${note["courseCode"]} | Fecha: ${note["noteDate"] ?: note["createdAt"]}"
            note["summary"]?.toString()?.takeIf(String::isNotBlank)?.let { summary ->
                lines += "  Resumen IA:"
                summary.split('\n').forEach { lines += "    $it" }
            }
            lines += "  Apunte capturado:"
            note["content"]?.toString().orEmpty().split('\n').forEach { lines += "    $it" }
        }

        lines += ""
        lines += "Cursos e inscripciones (${(data["enrollments"] as? List<*>)?.size ?: 0})"
        (data["enrollments"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { enrollment ->
            lines += "- ${enrollment["courseCode"]}: ${enrollment["courseName"]} | ${enrollment["enrolledAt"]}"
        }

        lines += ""
        lines += "Historial de evaluaciones (${(data["assessmentAttempts"] as? List<*>)?.size ?: 0})"
        (data["assessmentAttempts"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { attempt ->
            lines += "- ${attempt["assessmentTitle"]} | Resultado: ${attempt["score"] ?: "En progreso"} | ${attempt["completedAt"] ?: attempt["startedAt"]}"
        }

        lines += ""
        lines += "Cuestionarios creados (${(data["createdAssessments"] as? List<*>)?.size ?: 0})"
        (data["createdAssessments"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { assessment ->
            lines += "- ${assessment["title"]} | ${assessment["type"]} | ${assessment["difficulty"]}"
            (assessment["questions"] as? List<*>)?.forEachIndexed { index, question -> lines += "  Pregunta ${index + 1}: $question" }
        }

        val wrapped = lines.flatMap(::wrapLine)
        PDDocument().use { document ->
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            var page = PDPage()
            document.addPage(page)
            var stream = PDPageContentStream(document, page)
            var y = 750f
            try {
                wrapped.forEach { line ->
                    if (y < 55f) {
                        stream.close()
                        page = PDPage()
                        document.addPage(page)
                        stream = PDPageContentStream(document, page)
                        y = 750f
                    }
                    stream.beginText()
                    stream.setFont(font, 10f)
                    stream.newLineAtOffset(48f, y)
                    stream.showText(line.replace(Regex("[^\\u0020-\\u007E\\u00A0-\\u00FF]"), "?"))
                    stream.endText()
                    y -= 14f
                }
            } finally {
                stream.close()
            }
            return ByteArrayOutputStream().also(document::save).toByteArray()
        }
    }

    private fun wrapLine(line: String): List<String> {
        if (line.isEmpty()) return listOf("")
        val words = line.split(Regex("\\s+"))
        val result = mutableListOf<String>()
        var current = StringBuilder()
        words.forEach { word ->
            if (current.isNotEmpty() && current.length + word.length + 1 > 95) {
                result += current.toString()
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word.take(95))
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun studyActivityDays(data: Map<String, Any?>): Set<java.time.LocalDate> = buildSet {
        (data["notes"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { note ->
            (note["noteDate"]?.toString() ?: note["createdAt"]?.toString()?.take(10))?.let { value ->
                runCatching { add(java.time.LocalDate.parse(value)) }
            }
        }
        (data["assessmentAttempts"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { attempt ->
            attempt["startedAt"]?.toString()?.take(10)?.let { value ->
                runCatching { add(java.time.LocalDate.parse(value)) }
            }
        }
    }

    private fun longestStreak(days: Set<java.time.LocalDate>): Int {
        var best = 0
        var current = 0
        var previous: java.time.LocalDate? = null
        days.sorted().forEach { day ->
            current = if (previous?.plusDays(1) == day) current + 1 else 1
            best = maxOf(best, current)
            previous = day
        }
        return best
    }
}
