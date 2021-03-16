package no.nav.syfo.persistering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import java.util.UUID
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.log
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.persistering.handleRegistration
import no.nav.syfo.saf.service.SafJournalpostService
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.sykmelder.exception.SykmelderNotFoundException
import no.nav.syfo.sykmelder.exception.UnauthorizedException
import no.nav.syfo.sykmelder.service.SykmelderService
import no.nav.syfo.sykmelding.SykmeldingJobService
import no.nav.syfo.util.getAccessTokenFromAuthHeader

@KtorExperimentalAPI
fun Route.sendPapirSykmeldingManuellOppgave(
    sykmeldingJobService: SykmeldingJobService,
    manuellOppgaveService: ManuellOppgaveService,
    oppgaveClient: OppgaveClient,
    kuhrsarClient: SarClient,
    dokArkivClient: DokArkivClient,
    safJournalpostService: SafJournalpostService,
    regelClient: RegelClient,
    pdlService: PdlPersonService,
    sykmelderService: SykmelderService,
    authorizationService: AuthorizationService
) {
    route("/api/v1") {
        post("/oppgave/{oppgaveid}/send") {
            val oppgaveId = call.parameters["oppgaveid"]?.toIntOrNull()

            log.info("Mottok kall til POST /api/v1/oppgave/$oppgaveId/send")

            val accessToken = getAccessTokenFromAuthHeader(call.request)
            val callId = UUID.randomUUID().toString()
            val navEnhet = call.request.headers["X-Nav-Enhet"]

            val smRegistreringManuell: SmRegistreringManuell = call.receive()

            when {
                oppgaveId == null -> {
                    log.error("Path parameter mangler eller er feil formattert: oppgaveid")
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "Path parameter mangler eller er feil formattert: oppgaveid"
                    )
                }
                accessToken == null -> {
                    log.error("Mangler JWT Bearer token i HTTP header")
                    call.respond(HttpStatusCode.Unauthorized, "Mangler JWT Bearer token i HTTP header")
                }
                navEnhet == null -> {
                    log.error("Mangler X-Nav-Enhet i http header")
                    call.respond(HttpStatusCode.BadRequest, "Mangler X-Nav-Enhet i HTTP header")
                }
                else -> {
                    try {

                        handleRegistration(
                            smRegistreringManuell,
                            sykmelderService,
                            accessToken,
                            callId,
                            pdlService,
                            kuhrsarClient,
                            regelClient,
                            authorizationService,
                            sykmeldingJobService,
                            oppgaveClient,
                            dokArkivClient,
                            safJournalpostService,
                            oppgaveId,
                            navEnhet,
                            manuellOppgaveService
                        )
                    } catch (e: SykmelderNotFoundException) {
                        log.warn("Caught SykmelderNotFoundException", e)
                        call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt ved uthenting av behandler")
                    } catch (e: UnauthorizedException) {
                        log.warn("Caught UnauthorizedException", e)
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            "Et eller flere av systemene rapporterer feil knyttet til tilgangskontroll"
                        )
                    } catch (e: ValidationException) {
                        log.warn("Caught ValidationException", e)
                        call.respond(HttpStatusCode.BadRequest, e.validationResult)
                    }
                }
            }
        }
    }
}
