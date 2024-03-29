package no.nav.syfo.testutil

import io.mockk.every
import io.mockk.mockk
import java.sql.Connection
import no.nav.syfo.Environment
import no.nav.syfo.db.Database
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import org.testcontainers.containers.PostgreSQLContainer

fun Connection.dropData() {
    use { connection ->
        connection.prepareStatement("DELETE FROM sendt_sykmelding_history").executeUpdate()
        connection.prepareStatement("DELETE FROM manuelloppgave").executeUpdate()
        connection.prepareStatement("DELETE FROM job").executeUpdate()
        connection.prepareStatement("DELETE FROM sendt_sykmelding").executeUpdate()
        connection.commit()
    }
}

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:12")

class TestDB : DatabaseInterface {

    companion object {
        var database: DatabaseInterface
        private val psqlContainer: PsqlContainer =
            PsqlContainer()
                .withCommand("postgres", "-c", "wal_level=logical")
                .withExposedPorts(5432)
                .withUsername("username")
                .withPassword("password")
                .withDatabaseName("database")
                .withInitScript("db/testdb-init.sql")

        init {
            psqlContainer.start()
            val mockEnv = mockk<Environment>(relaxed = true)
            every { mockEnv.databaseUsername } returns "username"
            every { mockEnv.databasePassword } returns "password"
            every { mockEnv.dbName } returns "database"
            every { mockEnv.jdbcUrl() } returns psqlContainer.jdbcUrl
            try {
                database = Database(mockEnv)
            } catch (ex: Exception) {
                log.error("error", ex)
                database = Database(mockEnv)
            }
        }
    }

    override val connection: Connection
        get() = database.connection

    fun dropData() {
        this.connection.dropData()
    }
}
