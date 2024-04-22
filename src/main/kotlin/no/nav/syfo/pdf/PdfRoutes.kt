package no.nav.syfo.pdf

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

fun Route.registerPdfRoutes(
    pdfService: PdfService,
) {
    val logger = LoggerFactory.getLogger(this::class.java)

    route("/api/v1") {
        get("/pdf/{oppgaveId}/{dokumentInfoId}") {
            val oppgaveId = call.parameters["oppgaveId"]
            val dokumentInfoId = call.parameters["dokumentInfoId"]

            if (oppgaveId == null || dokumentInfoId == null) {
                logger.warn("Bad on PDF-request, oppgaveId: ${oppgaveId}, dokumentInfoId: ${dokumentInfoId}")
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val pdf = pdfService.getDocument(
                oppgaveId, dokumentInfoId,
                call.getRequestMeta(),
            )

            when (pdf) {
                is PdfDocument.Error -> {
                    logger.error("Unable to fetch PDF, reason: ${pdf.value}")
                    call.respond(HttpStatusCode.InternalServerError)
                }

                is PdfDocument.Good -> call.respondBytes(
                    pdf.value,
                    ContentType.Application.Pdf,
                    HttpStatusCode.OK,
                )
            }
        }
    }
}

private fun ApplicationCall.getRequestMeta(): RequestMeta {
    return RequestMeta(
        requestId = request.headers["X-Correlation-ID"] ?: request.headers["X-Request-ID"] ?: "",
        accessToken = request.headers["Authorization"]?.removePrefix("Bearer ") ?: "",
    )
}
