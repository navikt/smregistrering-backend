package no.nav.syfo.testutil

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.db.Database
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.db.VaultCredentials
import no.nav.syfo.log
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection

fun Connection.dropData() {
    use { connection ->
        connection.prepareStatement("DELETE FROM manuelloppgave").executeUpdate()
        connection.prepareStatement("DELETE FROM job").executeUpdate()
        connection.prepareStatement("DELETE FROM sendt_sykmelding").executeUpdate()
        connection.prepareStatement("DELETE FROM sendt_sykmelding_history").executeUpdate()
        connection.commit()
    }
}

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:12")

class TestDB : DatabaseInterface {

    companion object {
        var database: DatabaseInterface
        val vaultCredentialService = mockk<VaultCredentialService>()
        private val psqlContainer: PsqlContainer = PsqlContainer()
            .withExposedPorts(5432)
            .withUsername("username")
            .withPassword("password")
            .withDatabaseName("database")
            .withInitScript("db/testdb-init.sql")

        init {
            psqlContainer.start()
            val mockEnv = mockk<Environment>(relaxed = true)
            every { mockEnv.mountPathVault } returns ""
            every { mockEnv.databaseName } returns "database"
            every { mockEnv.smregistreringbackendDBURL } returns psqlContainer.jdbcUrl
            every { vaultCredentialService.renewCredentialsTaskData = any() } returns Unit
            every { vaultCredentialService.getNewCredentials(any(), any(), any()) } returns VaultCredentials(
                "1",
                "username",
                "password"
            )
            try {
                database = Database(mockEnv, vaultCredentialService)
            } catch (ex: Exception) {
                log.error("error", ex)
                database = Database(mockEnv, vaultCredentialService)
            }
        }
    }

    override val connection: Connection
        get() = database.connection

    fun dropData() {
        this.connection.dropData()
    }
}
