package no.nav.syfo.sykmelding.jobs.model

import java.time.OffsetDateTime

enum class JOBNAME {
    SENDT_TO_SYFOSERVICE,
    SENDT_SYKMELDING,
}

enum class JOBSTATUS {
    NEW, DONE, IN_PROGRESS
}

data class Job(
    val sykmeldingId: String,
    val name: JOBNAME,
    val status: JOBSTATUS,
    val updated: OffsetDateTime,
)
