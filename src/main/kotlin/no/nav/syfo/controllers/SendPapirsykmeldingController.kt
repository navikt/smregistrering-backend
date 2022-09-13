package no.nav.syfo.controllers

import io.ktor.http.HttpStatusCode
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.log
import no.nav.syfo.model.FerdigstillRegistrering
import no.nav.syfo.model.ManuellOppgaveDTO
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SendtSykmeldingHistory
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.Utfall
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.persistering.api.checkValidState
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.JournalpostService
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.service.Veileder
import no.nav.syfo.service.toSykmelding
import no.nav.syfo.sykmelder.service.SykmelderService
import no.nav.syfo.sykmelding.SendtSykmeldingService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatMarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.isWhitelisted
import no.nav.syfo.util.logNAVIdentTokenToSecureLogsWhenNoAccess
import no.nav.syfo.util.mapsmRegistreringManuelltTilFellesformat
import no.nav.syfo.util.toString
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class SendPapirsykmeldingController(
    private val sykmelderService: SykmelderService,
    private val pdlService: PdlPersonService,
    private val kuhrsarClient: SarClient,
    private val regelClient: RegelClient,
    private val authorizationService: AuthorizationService,
    private val sendtSykmeldingService: SendtSykmeldingService,
    private val oppgaveService: OppgaveService,
    private val journalpostService: JournalpostService,
    private val manuellOppgaveDAO: ManuellOppgaveDAO
) {
    suspend fun sendPapirsykmelding(
        smRegistreringManuell: SmRegistreringManuell,
        accessToken: String,
        callId: String,
        sykmeldingId: String,
        navEnhet: String
    ): HttpServiceResponse {
        val manueoppgaveDTOList = manuellOppgaveDAO.hentFerdigstiltManuellOppgave(sykmeldingId)
        return handleSendPapirsykmelding(
            manuellOppgaveDTOList = manueoppgaveDTOList,
            isUpdate = true,
            accessToken = accessToken,
            smRegistreringManuell = smRegistreringManuell,
            callId = callId,
            navEnhet = navEnhet,
            oppgaveId = null
        )
    }

    suspend fun sendPapirsykmelding(
        smRegistreringManuell: SmRegistreringManuell,
        accessToken: String,
        callId: String,
        oppgaveId: Int,
        navEnhet: String,
        isUpdate: Boolean = false
    ): HttpServiceResponse {
        val manuellOppgaveDTOList = manuellOppgaveDAO.hentManuellOppgaver(oppgaveId, ferdigstilt = isUpdate)
        return handleSendPapirsykmelding(
            manuellOppgaveDTOList,
            isUpdate,
            accessToken,
            smRegistreringManuell,
            callId,
            oppgaveId,
            navEnhet
        )
    }

    private suspend fun handleSendPapirsykmelding(
        manuellOppgaveDTOList: List<ManuellOppgaveDTO>,
        isUpdate: Boolean,
        accessToken: String,
        smRegistreringManuell: SmRegistreringManuell,
        callId: String,
        oppgaveId: Int?,
        navEnhet: String
    ): HttpServiceResponse {
        if (!manuellOppgaveDTOList.isNullOrEmpty()) {
            val manuellOppgave: ManuellOppgaveDTO = manuellOppgaveDTOList.first()
            val sykmeldingId = manuellOppgave.sykmeldingId
            val journalpostId = manuellOppgave.journalpostId
            val dokumentInfoId = manuellOppgave.dokumentInfoId

            val loggingMeta = LoggingMeta(
                mottakId = sykmeldingId,
                dokumentInfoId = dokumentInfoId,
                msgId = sykmeldingId,
                sykmeldingId = sykmeldingId,
                journalpostId = journalpostId
            )

            val hasAccess = when (isUpdate) {
                true -> authorizationService.hasSuperuserAccess(accessToken, smRegistreringManuell.pasientFnr)
                false -> authorizationService.hasAccess(accessToken, smRegistreringManuell.pasientFnr)
            }

            if (hasAccess) {
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
                    kuhrsarClient.getSamhandler(sykmelder.fnr!!, sykmeldingId)
                )
                if (samhandlerPraksis == null) {
                    log.info("Samhandlerpraksis ikke funnet for hpr-nummer ${sykmelder.hprNummer}")
                }

                val fellesformat = mapsmRegistreringManuelltTilFellesformat(
                    smRegistreringManuell = smRegistreringManuell,
                    pdlPasient = pasient,
                    sykmelder = sykmelder,
                    sykmeldingId = sykmeldingId,
                    datoOpprettet = manuellOppgave.datoOpprettet?.toLocalDateTime(),
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
                    mottattDato = manuellOppgave.datoOpprettet?.toLocalDateTime()
                        ?: msgHead.msgInfo.genDate,
                    rulesetVersion = healthInformation.regelSettVersjon,
                    fellesformat = fellesformatMarshaller.toString(fellesformat),
                    tssid = samhandlerPraksis?.tss_ident ?: "",
                    merknader = createMerknad(sykmelding),
                    partnerreferanse = null,
                    legeHelsepersonellkategori = sykmelder.godkjenninger?.getHelsepersonellKategori(),
                    legeHprNr = sykmelder.hprNummer,
                    vedlegg = null
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

                val ferdigstillRegistrering = FerdigstillRegistrering(
                    oppgaveId = oppgaveId,
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId,
                    pasientFnr = receivedSykmelding.personNrPasient,
                    sykmeldingId = sykmeldingId,
                    sykmelder = sykmelder,
                    navEnhet = navEnhet,
                    veileder = authorizationService.getVeileder(accessToken),
                    avvist = false,
                    oppgave = null
                )

                if (!validationResult.ruleHits.isWhitelisted()) {
                    return handleBrokenRule(validationResult, oppgaveId)
                } else {

                    return handleOK(
                        validationResult,
                        receivedSykmelding,
                        ferdigstillRegistrering,
                        loggingMeta,
                        accessToken,
                    )
                }
            } else {
                logNAVIdentTokenToSecureLogsWhenNoAccess(accessToken)
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
                    "ValidationResult har status OK, men inneholder ruleHits som ikke er hvitelistet, {}",
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
        receivedSykmelding: ReceivedSykmelding,
        ferdigstillRegistrering: FerdigstillRegistrering,
        loggingMeta: LoggingMeta,
        accessToken: String,
    ): HttpServiceResponse {
        when (validationResult.status) {
            Status.OK, Status.MANUAL_PROCESSING -> {
                val veileder = authorizationService.getVeileder(accessToken)

                if (ferdigstillRegistrering.oppgaveId != null) {
                    journalpostService.ferdigstillJournalpost(accessToken, ferdigstillRegistrering, loggingMeta)
                    oppgaveService.ferdigstillOppgave(ferdigstillRegistrering, null, loggingMeta, ferdigstillRegistrering.oppgaveId)
                }

                insertSykmeldingAndCreateJobs(receivedSykmelding, ferdigstillRegistrering, veileder)

                manuellOppgaveDAO.ferdigstillSmRegistering(
                    sykmeldingId = ferdigstillRegistrering.sykmeldingId,
                    utfall = Utfall.OK,
                    ferdigstiltAv = veileder.veilederIdent
                ).let {
                    return if (it > 0) {
                        HttpServiceResponse(HttpStatusCode.NoContent)
                    } else {
                        log.error(
                            "Ferdigstilling av manuelt registrert papirsykmelding feilet ved databaseoppdatering {}",
                            StructuredArguments.keyValue("oppgaveId", ferdigstillRegistrering.oppgaveId)
                        )
                        HttpServiceResponse(
                            HttpStatusCode.InternalServerError,
                            "Fant ingen uløst oppgave for oppgaveId ${ferdigstillRegistrering.oppgaveId}"
                        )
                    }
                }
            }
            else -> {
                log.error("Ukjent status: ${validationResult.status} , papirsykmeldinger manuell registering kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING")
                return HttpServiceResponse(HttpStatusCode.InternalServerError)
            }
        }
    }

    private fun insertSykmeldingAndCreateJobs(
        receivedSykmelding: ReceivedSykmelding,
        ferdigstillRegistrering: FerdigstillRegistrering,
        veileder: Veileder,
    ) {
        val sendtSykmeldingHistory = SendtSykmeldingHistory(
            UUID.randomUUID().toString(),
            ferdigstillRegistrering.sykmeldingId,
            veileder.veilederIdent,
            OffsetDateTime.now(ZoneOffset.UTC),
            receivedSykmelding
        )
        sendtSykmeldingService.insertSendtSykmeldingHistory(sendtSykmeldingHistory = sendtSykmeldingHistory)
        sendtSykmeldingService.upsertSendtSykmelding(receivedSykmelding)
        sendtSykmeldingService.createJobs(receivedSykmelding)
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
