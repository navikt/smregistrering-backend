package no.nav.syfo.sykmelder.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.util.UUID
import no.nav.syfo.log
import no.nav.syfo.sykmelder.exception.SykmelderNotFoundException
import no.nav.syfo.sykmelder.exception.UnauthorizedException
import no.nav.syfo.sykmelder.service.SykmelderService
import no.nav.syfo.util.padHpr

fun Route.sykmelderApi(
    sykmelderService: SykmelderService,
) {
    route("/api/v1") {
        get("/sykmelder/{hprNummer}") {
            val hprNummer = call.parameters["hprNummer"]?.toIntOrNull()

            log.info("Mottok kall til GET /api/v1/sykmelder/$hprNummer")
            when (hprNummer) {
                null -> {
                    log.info("Ugyldig path parameter: hprNummer")
                    call.respond(HttpStatusCode.BadRequest)
                }
                else -> {
                    val callId = UUID.randomUUID().toString()
                    try {
                        val sykmelder =
                            sykmelderService.hentSykmelder(
                                padHpr(hprNummer.toString()) ?: hprNummer.toString(),
                                callId,
                            )
                        call.respond(sykmelder)
                    } catch (e: SykmelderNotFoundException) {
                        log.warn("Caught SykmelderNotFoundException", e)
                        call.respond(HttpStatusCode.NotFound)
                    } catch (e: UnauthorizedException) {
                        log.warn("Caught UnauthorizedException", e)
                        call.respond(HttpStatusCode.Unauthorized)
                    }
                }
            }
        }
    }
}
