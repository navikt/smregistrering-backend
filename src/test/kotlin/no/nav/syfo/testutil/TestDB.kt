package no.nav.syfo.testutil

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.syfo.db.DatabaseInterface
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import javax.sql.DataSource

fun Connection.dropData() {
    use { connection ->
        connection.prepareStatement("DELETE FROM manuelloppgave").executeUpdate()
        connection.prepareStatement("DELETE FROM job").executeUpdate()
        connection.prepareStatement("DELETE FROM sendt_sykmelding").executeUpdate()
        connection.commit()
    }
}

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:12")

class PsqlContainerDatabase private constructor() : DatabaseInterface {

    companion object {
        val database = PsqlContainerDatabase()
    }

    private val psqlContainer: PsqlContainer
    private val dataSource: DataSource
    init {
        val databaseUsername = "username"
        val databasePassword = "password"
        val databaseName = "smregistrering"
        psqlContainer = PsqlContainer().withUsername(databaseUsername).withPassword(databasePassword).withDatabaseName(databaseName)
        psqlContainer.start()
        dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = psqlContainer.jdbcUrl
                username = databaseUsername
                password = databasePassword
                maximumPoolSize = 10
                minimumIdle = 3
                idleTimeout = 10001
                maxLifetime = 300000
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            }
        )
        Flyway.configure().run {
            dataSource(psqlContainer.jdbcUrl, databaseUsername, databasePassword).load().migrate()
        }
    }

    override val connection: Connection
        get() = dataSource.connection
}
