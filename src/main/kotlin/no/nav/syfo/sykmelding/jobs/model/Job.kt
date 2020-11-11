package no.nav.syfo.sykmelding.jobs.model

import java.time.OffsetDateTime

enum class JOB_NAME {
    SENDT_TO_SYFOSERVICE,
    SENDT_SYKMELDING
}

enum class JOB_STATUS {
    NEW, DONE, IN_PROGRESS
}

data class Job(
    val sykmeldingId: String,
    val name: JOB_NAME,
    val status: JOB_STATUS,
    val updated: OffsetDateTime
)
