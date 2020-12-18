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
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.model.Status
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.persistering.handleOKOppgave
import no.nav.syfo.saf.service.SafJournalpostService
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.service.mapsmRegistreringManuelltTilFellesformat
import no.nav.syfo.service.toSykmelding
import no.nav.syfo.sykmelder.exception.SykmelderNotFoundException
import no.nav.syfo.sykmelder.exception.UnauthorizedException
import no.nav.syfo.sykmelder.service.SykmelderService
import no.nav.syfo.sykmelding.SykmeldingJobService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatMarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.getAccessTokenFromAuthHeader
import no.nav.syfo.util.isAllRulesWhitelisted
import no.nav.syfo.util.toString

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

                        val manuellOppgaveDTOList = manuellOppgaveService.hentManuellOppgaver(oppgaveId)
                        if (!manuellOppgaveDTOList.isNullOrEmpty()) {
                            val sykmeldingId = manuellOppgaveDTOList.first().sykmeldingId
                            val journalpostId = manuellOppgaveDTOList.first().journalpostId
                            val dokumentInfoId = manuellOppgaveDTOList.first().dokumentInfoId

                            val loggingMeta = LoggingMeta(
                                mottakId = sykmeldingId,
                                dokumentInfoId = dokumentInfoId,
                                msgId = sykmeldingId,
                                sykmeldingId = sykmeldingId,
                                journalpostId = journalpostId
                            )

                            if (authorizationService.hasAccess(accessToken, smRegistreringManuell.pasientFnr)) {

                                validate(smRegistreringManuell)

                                val sykmelderHpr = smRegistreringManuell.behandler.hpr

                                if (sykmelderHpr.isNullOrEmpty()) {
                                    log.error("HPR-nummer mangler {}", fields(loggingMeta))
                                    call.respond(HttpStatusCode.BadRequest, "Mangler HPR-nummer for behandler")
                                }

                                log.info("Henter sykmelder fra HPR og PDL")
                                val sykmelder = sykmelderService.hentSykmelder(
                                    sykmelderHpr!!,
                                    accessToken,
                                    callId
                                )

                                log.info("Henter pasient fra PDL {} ", loggingMeta)
                                val pasient = pdlService.getPdlPerson(
                                    fnr = smRegistreringManuell.pasientFnr,
                                    userToken = accessToken,
                                    callId = callId
                                )

                                if (pasient.fnr == null || pasient.aktorId == null) {
                                    log.error("Pasientens altørId eller fnr finnes ikke i PDL")
                                    call.respond(HttpStatusCode.InternalServerError)
                                }

                                val samhandlerPraksis = findBestSamhandlerPraksis(
                                    kuhrsarClient.getSamhandler(sykmelder.fnr!!)
                                )
                                if (samhandlerPraksis == null) {
                                    log.info("Samhandlerpraksis ikke funnet for hpr-nummer ${sykmelder.hprNummer}")
                                }

                                val fellesformat = mapsmRegistreringManuelltTilFellesformat(
                                    smRegistreringManuell = smRegistreringManuell,
                                    pdlPasient = pasient,
                                    sykmelder = sykmelder,
                                    sykmeldingId = sykmeldingId,
                                    datoOpprettet = manuellOppgaveDTOList.firstOrNull()?.datoOpprettet?.toLocalDateTime()
                                )

                                val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
                                val msgHead = fellesformat.get<XMLMsgHead>()

                                val sykmelding = healthInformation.toSykmelding(
                                    sykmeldingId = sykmeldingId,
                                    pasientAktoerId = pasient.aktorId!!,
                                    legeAktoerId = sykmelder.aktorId!!,
                                    msgId = sykmeldingId,
                                    signaturDato = msgHead.msgInfo.genDate
                                )

                                val receivedSykmelding = ReceivedSykmelding(
                                    sykmelding = sykmelding,
                                    personNrPasient = pasient.fnr!!,
                                    tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
                                    personNrLege = sykmelder.fnr,
                                    navLogId = sykmeldingId,
                                    msgId = sykmeldingId,
                                    legekontorOrgNr = null,
                                    legekontorOrgName = "",
                                    legekontorHerId = null,
                                    legekontorReshId = null,
                                    mottattDato = manuellOppgaveDTOList.firstOrNull()?.datoOpprettet?.toLocalDateTime()
                                        ?: msgHead.msgInfo.genDate,
                                    rulesetVersion = healthInformation.regelSettVersjon,
                                    fellesformat = fellesformatMarshaller.toString(fellesformat),
                                    tssid = samhandlerPraksis?.tss_ident ?: ""
                                )

                                log.info(
                                    "Papirsykmelding manuell registering mappet til internt format uten feil {}",
                                    fields(loggingMeta)
                                )

                                val validationResult = regelClient.valider(receivedSykmelding, sykmeldingId)
                                log.info(
                                    "Resultat: {}, {}, {}",
                                    StructuredArguments.keyValue("ruleStatus", validationResult.status.name),
                                    StructuredArguments.keyValue(
                                        "ruleHits",
                                        validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }),
                                    fields(loggingMeta)
                                )

                                if (validationResult.ruleHits.isAllRulesWhitelisted()) {
                                    when (validationResult.status) {
                                        Status.OK, Status.MANUAL_PROCESSING -> {
                                            val veileder = authorizationService.getVeileder(accessToken)

                                            handleOKOppgave(
                                                sykmeldingJobService,
                                                receivedSykmelding = receivedSykmelding,
                                                loggingMeta = loggingMeta,
                                                oppgaveClient = oppgaveClient,
                                                dokArkivClient = dokArkivClient,
                                                safJournalpostService = safJournalpostService,
                                                accessToken = accessToken,
                                                sykmeldingId = sykmeldingId,
                                                journalpostId = journalpostId,
                                                oppgaveId = oppgaveId,
                                                veileder = veileder,
                                                sykmelder = sykmelder,
                                                navEnhet = navEnhet
                                            )

                                            if (manuellOppgaveService.ferdigstillSmRegistering(oppgaveId) > 0) {
                                                call.respond(HttpStatusCode.NoContent)
                                            } else {
                                                log.error(
                                                    "Ferdigstilling av papirsykmeldinger manuell registering i db feilet {}",
                                                    StructuredArguments.keyValue("oppgaveId", oppgaveId)
                                                )
                                                call.respond(HttpStatusCode.InternalServerError)
                                            }
                                        }
                                        else -> {
                                            log.error("Ukjent status: ${validationResult.status} , papirsykmeldinger manuell registering kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING")
                                            call.respond(HttpStatusCode.InternalServerError)
                                        }
                                    }
                                } else {
                                    when (validationResult.status) {
                                        Status.MANUAL_PROCESSING -> {
                                            log.info(
                                                "Ferdigstilling av papirsykmeldinger manuell registering traff regel MANUAL_PROCESSING {}",
                                                StructuredArguments.keyValue("oppgaveId", oppgaveId)
                                            )
                                            call.respond(HttpStatusCode.BadRequest, validationResult)
                                        }
                                        Status.OK -> {
                                            log.error(
                                                "ValidationResult har status OK, men inneholder ruleHits som ikke er hvitelistet",
                                                StructuredArguments.keyValue("oppgaveId", oppgaveId)
                                            )
                                            call.respond(
                                                HttpStatusCode.InternalServerError,
                                                "Noe gikk galt ved innsending av oppgave"
                                            )
                                        }
                                        else -> {
                                            log.error("Ukjent status: ${validationResult.status} , papirsykmeldinger manuell registering kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING")
                                            call.respond(
                                                HttpStatusCode.InternalServerError,
                                                "Noe gikk galt ved innsending av oppgave"
                                            )
                                        }
                                    }
                                }
                            } else {
                                log.warn(
                                    "Veileder har ikkje tilgang, {}, {}",
                                    StructuredArguments.keyValue("oppgaveId", oppgaveId), fields(loggingMeta)
                                )
                                call.respond(HttpStatusCode.Unauthorized, "Veileder har ikke tilgang til oppgaven")
                            }
                        } else {
                            log.warn(
                                "Henting av papirsykmeldinger manuell registering returente null {}",
                                StructuredArguments.keyValue("oppgaveId", oppgaveId)
                            )
                            call.respond(
                                HttpStatusCode.NotFound,
                                "Fant ingen uløste manuelle oppgaver med oppgaveid $oppgaveId"
                            )
                        }
                    } catch (e: SykmelderNotFoundException) {
                        log.warn("Caught SykmelderNotFoundException", e)
                        call.respond(HttpStatusCode.InternalServerError)
                    } catch (e: UnauthorizedException) {
                        log.warn("Caught UnauthorizedException", e)
                        call.respond(HttpStatusCode.Unauthorized)
                    } catch (e: ValidationException) {
                        log.warn("Caught ValidationException", e)
                        call.respond(HttpStatusCode.BadRequest, e.validationResult)
                    }
                }
            }
        }
    }
}
