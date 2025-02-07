package no.nav.syfo.sykmelding.db

import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZoneOffset.UTC
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.kafka.ReceivedSykmeldingWithTimestamp
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SendtSykmeldingHistory
import no.nav.syfo.objectMapper
import no.nav.syfo.util.toPGObject

fun DatabaseInterface.upsertSendtSykmelding(receivedSykmelding: ReceivedSykmelding) {
    connection.use { connection ->
        connection
            .prepareStatement(
                """
           insert into sendt_sykmelding(sykmelding_id, sykmelding, timestamp) values (?, ?, ?) on conflict (sykmelding_id) do update set sykmelding = ?
        """,
            )
            .use { ps ->
                ps.setString(1, receivedSykmelding.sykmelding.id)
                ps.setObject(2, toPGObject(receivedSykmelding))
                ps.setTimestamp(3, Timestamp.from(Instant.now()))
                ps.setObject(4, toPGObject(receivedSykmelding))
                ps.execute()
            }
        connection.commit()
    }
}

fun DatabaseInterface.insertSendtSykmeldingHistory(sendtSykmeldingHistory: SendtSykmeldingHistory) {
    connection.use { connection ->
        connection
            .prepareStatement(
                """
           INSERT INTO sendt_sykmelding_history(id, sykmelding_id, ferdigstilt_av, dato_ferdigstilt, sykmelding) 
           VALUES (?, ?, ?, ?, ?) 
        """,
            )
            .use { ps ->
                ps.setString(1, sendtSykmeldingHistory.id)
                ps.setString(2, sendtSykmeldingHistory.sykmeldingId)
                ps.setString(3, sendtSykmeldingHistory.ferdigstiltAv)
                ps.setTimestamp(
                    4,
                    Timestamp.from(sendtSykmeldingHistory.datoFerdigstilt.toInstant(UTC)),
                )
                ps.setObject(5, toPGObject(sendtSykmeldingHistory.receivedSykmelding))
                ps.executeUpdate()
            }
        connection.commit()
    }
}

fun DatabaseInterface.getSykmelding(sykmeldingId: String): ReceivedSykmelding? {
    return connection.use {
        it.prepareStatement(
                """
            select * from sendt_sykmelding where sykmelding_id = ?
        """,
            )
            .use {
                it.setString(1, sykmeldingId)
                it.executeQuery().toReceivedSykmelding()
            }
    }
}

fun DatabaseInterface.getAllSykmeldingWithTimestamp(): List<ReceivedSykmeldingWithTimestamp> {
    return connection.use {
        it.prepareStatement(
                """
            select * from sendt_sykmelding
        """,
            )
            .use { it.executeQuery().toList { toReceivedSykmeldingWithTimestamp() } }
    }
}

fun DatabaseInterface.geAlltSendtSykmeldingHistory(): List<SendtSykmeldingHistory> {
    return connection.use {
        it.prepareStatement(
                """
            SELECT * FROM sendt_sykmelding_history
            """,
            )
            .use { it.executeQuery().toList { toSendtSykmeldingHistoryList() } }
    }
}

private fun ResultSet.toSendtSykmeldingHistoryList(): SendtSykmeldingHistory {
    val receivedSykmelding = objectMapper.readValue<ReceivedSykmelding>(getString("sykmelding"))
    val sendtSykmeldingHistory =
        SendtSykmeldingHistory(
            sykmeldingId = getString("sykmelding_id").trim(),
            id = getString("id").trim(),
            ferdigstiltAv = getString("ferdigstilt_av").trim(),
            datoFerdigstilt = getTimestamp("dato_ferdigstilt").toLocalDateTime(),
            receivedSykmelding = receivedSykmelding,
        )
    return sendtSykmeldingHistory
}

private fun ResultSet.toReceivedSykmelding(): ReceivedSykmelding? {
    return when (next()) {
        true -> objectMapper.readValue<ReceivedSykmelding>(getString("sykmelding"))
        else -> null
    }
}

private fun ResultSet.toReceivedSykmeldingWithTimestamp(): ReceivedSykmeldingWithTimestamp {
    return ReceivedSykmeldingWithTimestamp(
        receivedSykmelding = objectMapper.readValue<ReceivedSykmelding>(getString("sykmelding")),
        timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
    )
}
