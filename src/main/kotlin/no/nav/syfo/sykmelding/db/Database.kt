package no.nav.syfo.sykmelding.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.objectMapper
import no.nav.syfo.util.toPGObject
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

fun DatabaseInterface.upsertSykmelding(receivedSykmelding: ReceivedSykmelding) {
    connection.use { connection ->
        upsertSykmelding(connection, receivedSykmelding)
        connection.commit()
    }
}

private fun upsertSykmelding(connection: Connection, receivedSykmelding: ReceivedSykmelding) {
    connection.prepareStatement(
        """
           insert into sendt_sykmelding(sykmelding_id, sykmelding, timestamp) values (?, ?, ?) on conflict (sykmelding_id) do update set sykmelding = ?
        """
    ).use { ps ->
        ps.setString(1, receivedSykmelding.sykmelding.id)
        ps.setObject(2, toPGObject(receivedSykmelding))
        ps.setTimestamp(3, Timestamp.from(Instant.now()))
        ps.setObject(4, toPGObject(receivedSykmelding))
        ps.execute()
    }
}

fun DatabaseInterface.getSykmelding(sykmeldingId: String): ReceivedSykmelding? {
    return connection.use {
        it.prepareStatement(
            """
            select * from sendt_sykmelding where sykmelding_id = ?
        """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toReceivedSykmelding()
        }
    }
}

private fun ResultSet.toReceivedSykmelding(): ReceivedSykmelding? {
    return when (next()) {
        true -> objectMapper.readValue<ReceivedSykmelding>(getString("sykmelding"))
        else -> null
    }
}
