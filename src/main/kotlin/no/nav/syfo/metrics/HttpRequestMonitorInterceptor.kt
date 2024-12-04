package no.nav.syfo.metrics

import io.ktor.server.application.PipelineCall
import io.ktor.server.request.path
import io.ktor.util.pipeline.PipelineInterceptor

val REGEX = """(\d{2,9})""".toRegex()

fun monitorHttpRequests(): PipelineInterceptor<Unit, PipelineCall> {
    return {
        val path = context.request.path()
        val label = REGEX.replace(path, ":id")
        val timer = HTTP_HISTOGRAM.labels(label).startTimer()
        proceed()
        timer.observeDuration()
    }
}
