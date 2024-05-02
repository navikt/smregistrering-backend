package no.nav.syfo.pdf

import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.saf.SafDokumentClient
import no.nav.syfo.saf.exception.SafForbiddenException
import no.nav.syfo.saf.exception.SafNotFoundException
import no.nav.syfo.service.AuthorizationService

data class RequestMeta(
    val requestId: String,
    val accessToken: String,
)

class PdfService(
    private val manuellOppgaveDAO: ManuellOppgaveDAO,
    private val dokumentClient: SafDokumentClient,
    private val authorizationService: AuthorizationService,
) {
    public suspend fun getDocument(
        oppgaveId: String,
        dokumentInfoId: String,
        context: RequestMeta
    ): PdfDocument {
        val manuellOppgaveList = manuellOppgaveDAO.hentManuellOppgaver(oppgaveId.toInt())
        val firstOppgave = manuellOppgaveList.firstOrNull()

        if (firstOppgave == null || firstOppgave.fnr.isNullOrEmpty()) {
            return PdfDocument.Error(PdfErrors.SAKSBEHANDLER_IKKE_TILGANG)
        }

        if (!authorizationService.hasAccess(context.accessToken, firstOppgave.fnr)) {
            return PdfDocument.Error(PdfErrors.SAKSBEHANDLER_IKKE_TILGANG)
        }

        try {
            val pdf =
                dokumentClient.hentDokument(
                    dokumentInfoId = dokumentInfoId,
                    journalpostId = firstOppgave.journalpostId,
                    sykmeldingId = firstOppgave.sykmeldingId,
                    accessToken = context.accessToken,
                    msgId = context.requestId,
                )

            if (pdf == null) {
                return PdfDocument.Error(PdfErrors.EMPTY_RESPONSE)
            }

            return PdfDocument.Good(pdf)
        } catch (e: SafForbiddenException) {
            return PdfDocument.Error(PdfErrors.SAKSBEHANDLER_IKKE_TILGANG)
        } catch (e: SafNotFoundException) {
            return PdfDocument.Error(PdfErrors.DOCUMENT_NOT_FOUND)
        } catch (e: Exception) {
            return PdfDocument.Error(PdfErrors.SAF_CLIENT_ERROR)
        }
    }
}

enum class PdfErrors {
    DOCUMENT_NOT_FOUND,
    SAF_CLIENT_ERROR,
    EMPTY_RESPONSE,
    SAKSBEHANDLER_IKKE_TILGANG,
}

sealed class PdfDocument {
    data class Good(val value: ByteArray) : PdfDocument()

    data class Error(val value: PdfErrors) : PdfDocument()
}
