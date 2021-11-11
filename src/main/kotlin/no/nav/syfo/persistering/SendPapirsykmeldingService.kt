package no.nav.syfo.persistering

import io.ktor.http.HttpStatusCode
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.log
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.Utfall
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.persistering.api.checkValidState
import no.nav.syfo.saf.service.SafJournalpostService
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.service.mapsmRegistreringManuelltTilFellesformat
import no.nav.syfo.service.toSykmelding
import no.nav.syfo.sykmelder.service.SykmelderService
import no.nav.syfo.sykmelding.SykmeldingJobService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatMarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.isWhitelisted
import no.nav.syfo.util.toString

class SendPapirsykmeldingService(
    private val sykmelderService: SykmelderService,
    private val pdlService: PdlPersonService,
    private val kuhrsarClient: SarClient,
    private val regelClient: RegelClient,
    private val authorizationService: AuthorizationService,
    private val sykmeldingJobService: SykmeldingJobService,
    private val oppgaveClient: OppgaveClient,
    private val dokArkivClient: DokArkivClient,
    private val safJournalpostService: SafJournalpostService,
    private val manuellOppgaveService: ManuellOppgaveService
) {

    suspend fun handleRegistration(
        smRegistreringManuell: SmRegistreringManuell,
        accessToken: String,
        callId: String,
        oppgaveId: Int,
        navEnhet: String
    ): HttpServiceResponse {
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
                val sykmelderHpr = smRegistreringManuell.behandler.hpr

                if (sykmelderHpr.isNullOrEmpty()) {
                    log.error("HPR-nummer mangler {}", StructuredArguments.fields(loggingMeta))
                    return HttpServiceResponse(HttpStatusCode.BadRequest, "Mangler HPR-nummer for behandler")
                }

                log.info("Henter sykmelder fra HPR og PDL")
                val sykmelder = sykmelderService.hentSykmelder(
                    sykmelderHpr,
                    callId
                )

                log.info("Henter pasient fra PDL {} ", loggingMeta)
                val pasient = pdlService.getPdlPerson(
                    fnr = smRegistreringManuell.pasientFnr,
                    callId = callId
                )

                if (pasient.fnr == null || pasient.aktorId == null) {
                    log.error("Pasientens aktørId eller fnr finnes ikke i PDL")
                    return HttpServiceResponse(HttpStatusCode.InternalServerError, "Fant ikke pasientens aktørid")
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
                    datoOpprettet = manuellOppgaveDTOList.firstOrNull()?.datoOpprettet?.toLocalDateTime(),
                    journalpostId = journalpostId
                )

                val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
                val msgHead = fellesformat.get<XMLMsgHead>()

                val sykmelding = healthInformation.toSykmelding(
                    sykmeldingId = sykmeldingId,
                    pasientAktoerId = pasient.aktorId,
                    legeAktoerId = sykmelder.aktorId!!,
                    msgId = sykmeldingId,
                    signaturDato = msgHead.msgInfo.genDate
                )

                val receivedSykmelding = ReceivedSykmelding(
                    sykmelding = sykmelding,
                    personNrPasient = pasient.fnr,
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
                    tssid = samhandlerPraksis?.tss_ident ?: "",
                    merknader = createMerknad(sykmelding),
                    partnerreferanse = null,
                    legeHelsepersonellkategori = sykmelder.godkjenninger?.getHelsepersonellKategori(),
                    legeHprNr = sykmelder.hprNummer
                )

                log.info(
                    "Papirsykmelding manuell registering mappet til internt format uten feil {}",
                    StructuredArguments.fields(loggingMeta)
                )

                val validationResult = regelClient.valider(receivedSykmelding, sykmeldingId)
                log.info(
                    "Resultat: {}, {}, {}",
                    StructuredArguments.keyValue("ruleStatus", validationResult.status.name),
                    StructuredArguments.keyValue(
                        "ruleHits",
                        validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }
                    ),
                    StructuredArguments.fields(loggingMeta)
                )

                checkValidState(smRegistreringManuell, sykmelder, validationResult)

                if (!validationResult.ruleHits.isWhitelisted()) {
                    return handleBrokenRule(validationResult, oppgaveId)
                } else {
                    return handleOK(
                        validationResult,
                        accessToken,
                        receivedSykmelding,
                        loggingMeta,
                        sykmeldingId,
                        journalpostId,
                        dokumentInfoId,
                        oppgaveId,
                        sykmelder,
                        navEnhet
                    )
                }
            } else {
                return handleAccessDenied(oppgaveId, loggingMeta)
            }
        }
        return handleNotFound(oppgaveId)
    }

    private fun handleNotFound(
        oppgaveId: Int?
    ): HttpServiceResponse {
        log.warn(
            "Henting av papirsykmeldinger manuell registering returente null {}",
            StructuredArguments.keyValue("oppgaveId", oppgaveId)
        )

        return HttpServiceResponse(
            HttpStatusCode.NotFound,
            "Fant ingen uløste manuelle oppgaver med oppgaveid $oppgaveId"
        )
    }

    private fun handleAccessDenied(
        oppgaveId: Int?,
        loggingMeta: LoggingMeta
    ): HttpServiceResponse {
        log.warn(
            "Veileder har ikkje tilgang, {}, {}",
            StructuredArguments.keyValue("oppgaveId", oppgaveId), StructuredArguments.fields(loggingMeta)
        )
        return HttpServiceResponse(HttpStatusCode.Forbidden, "Veileder har ikke tilgang til oppgaven")
    }

    private fun handleBrokenRule(
        validationResult: ValidationResult,
        oppgaveId: Int?
    ): HttpServiceResponse {
        when (validationResult.status) {
            Status.MANUAL_PROCESSING -> {
                log.info(
                    "Ferdigstilling av papirsykmeldinger manuell registering traff regel MANUAL_PROCESSING {}",
                    StructuredArguments.keyValue("oppgaveId", oppgaveId)
                )
                return HttpServiceResponse(HttpStatusCode.BadRequest, validationResult)
            }
            Status.OK -> {
                log.error(
                    "ValidationResult har status OK, men inneholder ruleHits som ikke er hvitelistet",
                    StructuredArguments.keyValue("oppgaveId", oppgaveId)
                )
                HttpServiceResponse(
                    HttpStatusCode.InternalServerError,
                    "Noe gikk galt ved innsending av oppgave"
                )
            }
            else -> {
                log.error("Ukjent status: ${validationResult.status} , papirsykmeldinger manuell registering kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING")
                return HttpServiceResponse(
                    HttpStatusCode.InternalServerError,
                    "En uforutsett feil oppsto ved validering av oppgaven"
                )
            }
        }
        return HttpServiceResponse(HttpStatusCode.InternalServerError)
    }

    private suspend fun handleOK(
        validationResult: ValidationResult,
        accessToken: String,
        receivedSykmelding: ReceivedSykmelding,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
        journalpostId: String,
        dokumentInfoId: String?,
        oppgaveId: Int,
        sykmelder: Sykmelder,
        navEnhet: String
    ): HttpServiceResponse {
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
                    dokumentInfoId = dokumentInfoId,
                    oppgaveId = oppgaveId,
                    veileder = veileder,
                    sykmelder = sykmelder,
                    navEnhet = navEnhet
                )

                manuellOppgaveService.ferdigstillSmRegistering(
                    oppgaveId = oppgaveId,
                    utfall = Utfall.OK,
                    ferdigstiltAv = veileder.veilederIdent
                ).let {
                    return if (it > 0) {
                        HttpServiceResponse(HttpStatusCode.NoContent)
                    } else {
                        log.error(
                            "Ferdigstilling av manuelt registrert papirsykmelding feilet ved databaseoppdatering {}",
                            StructuredArguments.keyValue("oppgaveId", oppgaveId)
                        )
                        HttpServiceResponse(HttpStatusCode.InternalServerError, "Fant ingen uløst oppgave for oppgaveId $oppgaveId")
                    }
                }
            }
            else -> {
                log.error("Ukjent status: ${validationResult.status} , papirsykmeldinger manuell registering kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING")
                return HttpServiceResponse(HttpStatusCode.InternalServerError)
            }
        }
    }
}

private fun createMerknad(sykmelding: Sykmelding): List<Merknad>? {
    val behandletTidspunkt = sykmelding.behandletTidspunkt.toLocalDate()
    val terskel = sykmelding.perioder.map { it.fom }.minOrNull()?.plusDays(7)
    return if (behandletTidspunkt != null && terskel != null &&
        behandletTidspunkt > terskel
    ) {
        listOf(Merknad("TILBAKEDATERT_PAPIRSYKMELDING", null))
    } else {
        null
    }
}

fun List<Godkjenning>.getHelsepersonellKategori(): String? = when {
    find { it.helsepersonellkategori?.verdi == "LE" } != null -> "LE"
    find { it.helsepersonellkategori?.verdi == "TL" } != null -> "TL"
    find { it.helsepersonellkategori?.verdi == "MT" } != null -> "MT"
    find { it.helsepersonellkategori?.verdi == "FT" } != null -> "FT"
    find { it.helsepersonellkategori?.verdi == "KI" } != null -> "KI"
    else -> {
        val verdi = firstOrNull()?.helsepersonellkategori?.verdi
        log.warn("Signerende behandler har ikke en helsepersonellkategori($verdi) vi kjenner igjen")
        verdi
    }
}
data class HttpServiceResponse(
    val httpStatusCode: HttpStatusCode,
    val payload: Any? = null
)
