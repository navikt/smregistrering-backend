package no.nav.syfo.persistering

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.util.pipeline.PipelineContext
import net.logstash.logback.argument.StructuredArguments
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
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.persistering.api.validate
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
import no.nav.syfo.util.isAllRulesWhitelisted
import no.nav.syfo.util.toString

suspend fun PipelineContext<Unit, ApplicationCall>.handleRegistration(
    smRegistreringManuell: SmRegistreringManuell,
    sykmelderService: SykmelderService,
    accessToken: String,
    callId: String,
    pdlService: PdlPersonService,
    kuhrsarClient: SarClient,
    regelClient: RegelClient,
    authorizationService: AuthorizationService,
    sykmeldingJobService: SykmeldingJobService,
    oppgaveClient: OppgaveClient,
    dokArkivClient: DokArkivClient,
    safJournalpostService: SafJournalpostService,
    oppgaveId: Int,
    navEnhet: String,
    manuellOppgaveService: ManuellOppgaveService
) {

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
                log.error("HPR-nummer mangler {}", StructuredArguments.fields(loggingMeta))
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
                log.error("Pasientens aktørId eller fnr finnes ikke i PDL")
                call.respond(HttpStatusCode.InternalServerError, "Fant ikke pasientens aktørid ")
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
                StructuredArguments.fields(loggingMeta)
            )

            val validationResult = regelClient.valider(receivedSykmelding, sykmeldingId)
            log.info(
                "Resultat: {}, {}, {}",
                StructuredArguments.keyValue("ruleStatus", validationResult.status.name),
                StructuredArguments.keyValue(
                    "ruleHits",
                    validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }),
                StructuredArguments.fields(loggingMeta)
            )

            if (validationResult.ruleHits.isAllRulesWhitelisted()) {
                handleOK(
                    validationResult,
                    authorizationService,
                    accessToken,
                    sykmeldingJobService,
                    receivedSykmelding,
                    loggingMeta,
                    oppgaveClient,
                    dokArkivClient,
                    safJournalpostService,
                    sykmeldingId,
                    journalpostId,
                    dokumentInfoId,
                    oppgaveId,
                    sykmelder,
                    navEnhet,
                    manuellOppgaveService
                )
            } else {
                handleBrokenRule(validationResult, oppgaveId)
            }
        } else {
            handleAccessDenied(oppgaveId, loggingMeta)
        }
    } else {
        handleNotFound(oppgaveId)
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.handleNotFound(
    oppgaveId: Int?
) {
    log.warn(
        "Henting av papirsykmeldinger manuell registering returente null {}",
        StructuredArguments.keyValue("oppgaveId", oppgaveId)
    )
    call.respond(
        HttpStatusCode.NotFound,
        "Fant ingen uløste manuelle oppgaver med oppgaveid $oppgaveId"
    )
}

private suspend fun PipelineContext<Unit, ApplicationCall>.handleAccessDenied(
    oppgaveId: Int?,
    loggingMeta: LoggingMeta
) {
    log.warn(
        "Veileder har ikkje tilgang, {}, {}",
        StructuredArguments.keyValue("oppgaveId", oppgaveId), StructuredArguments.fields(loggingMeta)
    )
    call.respond(HttpStatusCode.Unauthorized, "Veileder har ikke tilgang til oppgaven")
}

private suspend fun PipelineContext<Unit, ApplicationCall>.handleBrokenRule(
    validationResult: ValidationResult,
    oppgaveId: Int?
) {
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
                "En uforutsett feil oppsto ved validering av oppgaven"
            )
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.handleOK(
    validationResult: ValidationResult,
    authorizationService: AuthorizationService,
    accessToken: String,
    sykmeldingJobService: SykmeldingJobService,
    receivedSykmelding: ReceivedSykmelding,
    loggingMeta: LoggingMeta,
    oppgaveClient: OppgaveClient,
    dokArkivClient: DokArkivClient,
    safJournalpostService: SafJournalpostService,
    sykmeldingId: String,
    journalpostId: String,
    dokumentInfoId: String?,
    oppgaveId: Int,
    sykmelder: Sykmelder,
    navEnhet: String,
    manuellOppgaveService: ManuellOppgaveService
) {
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

            if (manuellOppgaveService.ferdigstillSmRegistering(oppgaveId) > 0) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                log.error(
                    "Ferdigstilling av papirsykmeldinger manuell registering i db feilet {}",
                    StructuredArguments.keyValue("oppgaveId", oppgaveId)
                )
                call.respond(HttpStatusCode.InternalServerError, "Fant ikke en uløst oppgave for oppgaveId $oppgaveId")
            }
        }
        else -> {
            log.error("Ukjent status: ${validationResult.status} , papirsykmeldinger manuell registering kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING")
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}
