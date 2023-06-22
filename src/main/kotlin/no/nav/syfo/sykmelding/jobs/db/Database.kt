package no.nav.syfo.sykmelding.jobs.db

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.sykmelding.jobs.model.JOBNAME
import no.nav.syfo.sykmelding.jobs.model.JOBSTATUS
import no.nav.syfo.sykmelding.jobs.model.Job

private const val TRANSACTION_TIMEOUT = 60_000

fun DatabaseInterface.insertJobs(jobs: List<Job>) {
    connection.use { connection ->
        upsertJobs(connection, jobs)
        connection.commit()
    }
}

private fun upsertJobs(connection: Connection, jobs: List<Job>) {
    connection
        .prepareStatement(
            """
            INSERT INTO job(sykmelding_id, name, created, updated, status)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (sykmelding_id, name) DO UPDATE SET updated = ?,
                                                            status  = ?;
            """,
        )
        .use {
            for (job in jobs) {
                var i = 1
                it.setString(i++, job.sykmeldingId)
                it.setString(i++, job.name.name)
                it.setTimestamp(i++, Timestamp.from(job.updated.toInstant()))
                it.setTimestamp(i++, Timestamp.from(job.updated.toInstant()))
                it.setString(i++, job.status.name)
                it.setTimestamp(i++, Timestamp.from(job.updated.toInstant()))
                it.setString(i, job.status.name)
                it.addBatch()
            }
            it.executeBatch()
        }
}

fun DatabaseInterface.getNextJob(): Job? {
    return connection.use { connection ->
        try {
            var job = getJob(connection, JOBSTATUS.NEW)
            if (job != null) {
                job = job.copy(updated = OffsetDateTime.now(), status = JOBSTATUS.IN_PROGRESS)
                val updates = updateJob(connection, job)
                if (updates != 1) {
                    log.error("Error in database")
                    connection.rollback()
                    return null
                } else {
                    connection.commit()
                }
            }
            job
        } catch (ex: Exception) {
            connection.rollback()
            throw ex
        }
    }
}

fun DatabaseInterface.getJobStatus(status: JOBSTATUS): Job? {
    return connection.use { getJob(connection, status) }
}

fun DatabaseInterface.updateJob(job: Job) {
    connection.use { connection ->
        updateJob(connection, job)
        connection.commit()
    }
}

fun DatabaseInterface.resetJobs(): Int {
    val resetTimestamp = OffsetDateTime.now().minusHours(1)
    var updated = 0
    connection.use { connection ->
        connection
            .prepareStatement(
                """
           update job set status = '${JOBSTATUS.NEW.name}', updated = ? where status = '${JOBSTATUS.IN_PROGRESS.name}' and updated < ?
        """,
            )
            .use { ps ->
                ps.setTimestamp(1, Timestamp.from(Instant.now()))
                ps.setTimestamp(2, Timestamp.from(resetTimestamp.toInstant()))
                updated = ps.executeUpdate()
            }
        connection.commit()
    }
    return updated
}

private fun updateJob(connection: Connection, job: Job): Int {
    return connection
        .prepareStatement(
            """
                update job set status=?, updated=? where name=? and sykmelding_id=? 
            """,
        )
        .use {
            var i = 0
            it.setString(++i, job.status.name)
            it.setTimestamp(++i, Timestamp.from(job.updated.toInstant()))
            it.setString(++i, job.name.name)
            it.setString(++i, job.sykmeldingId)
            it.executeUpdate()
        }
}

private fun getJob(connection: Connection, status: JOBSTATUS): Job? {
    connection
        .prepareStatement("""SET idle_in_transaction_session_timeout = $TRANSACTION_TIMEOUT""")
        .execute()
    return connection
        .prepareStatement(
            """
            SELECT * from job where status = ? order by created desc limit 1 for update skip locked ;
        """,
        )
        .use {
            it.setString(1, status.name)
            it.executeQuery().toJob()
        }
}

private fun ResultSet.toJob(): Job? {
    return if (next()) {
        Job(
            sykmeldingId = getString("sykmelding_id"),
            updated = getTimestamp("updated").toInstant().atOffset(ZoneOffset.UTC),
            name = JOBNAME.valueOf(getString("name")),
            status = JOBSTATUS.valueOf(getString("status")),
        )
    } else {
        null
    }
}
