package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "smregistrering-backend"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val sm2013SmregistreringTopic: String = getEnvVar("KAFKA_PAPIR_SM_REGISTERING_TOPIC", "privat-syfo-papir-sm-registering"),
    val serviceuserUsernamePath: String = getEnvVar("SERVICE_USER_USERNAME"),
    val serviceuserPasswordPath: String = getEnvVar("SERVICE_USER_PASSWORD"),
    val smregistreringbackendDBURL: String = getEnvVar("SMREGISTERINGB_BACKEND_DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "smregistrering-backend"),
    val oidcWellKnownUriPath: String = getEnvVar("OIDC_WELL_KNOWN_URI"),
    val smregistreringBackendClientIdPath: String = getEnvVar("SMREGISTERING_BACKEND_CLIENT_ID_PATH"),
    val smregistreringUrl: String = getEnvVar("SMREGISTERING_URL"),
    val securityTokenUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL", "http://security-token-service/rest/v1/sts/token"),
    val oppgavebehandlingUrl: String = getEnvVar("OPPGAVEBEHANDLING_URL", "http://oppgave/api/v1/oppgaver"),
    val hentDokumentUrl: String = getEnvVar("HENT_DOKUMENT_URL"),
    val kuhrSarApiUrl: String = getEnvVar("KUHR_SAR_API_URL", "https://kuhr-sar-api.nais.adeo.no"),
    val dokArkivUrl: String = getEnvVar("DOK_ARKIV_URL"),
    val aadAccessTokenUrl: String = getEnvVar("AADACCESSTOKEN_URL"),
    val regelEndpointURL: String = getEnvVar("SYFOSMPAPIR_REGLER_ENDPOINT_URL", "http://syfosmpapirregler"),
    val smregistreringBackendClientSecretPath: String = getEnvVar("SMREGISTERING_BACKEND_CLIENT_SECRET_PATH"),
    val syfosmpapirregelClientIdPath: String = getEnvVar("SYFOSMPAIR_REGLER_CLIENT_ID_PATH"),
    val syfoserviceKafkaTopic: String = "privat-syfo-syfoservice-mq",
    val sm2013ManualHandlingTopic: String = getEnvVar("KAFKA_SM2013_MANUAL_TOPIC", "privat-syfo-sm2013-manuellBehandling"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val sm2013BehandlingsUtfallTopic: String = getEnvVar("KAFKA_SM2013_BEHANDLING_TOPIC", "privat-syfo-sm2013-behandlingsUtfall"),
    val smProduserOppgaveTopic: String = getEnvVar("KAFKA_PRODUSER_OPPGAVE_TOPIC", "aapen-syfo-oppgave-produserOppgave"),
    val syfoTilgangsKontrollClientUrl: String = getEnvVar("SYFOTILGANGSKONTROLL_URL", "http://syfo-tilgangskontroll/syfo-tilgangskontroll"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val developmentMode: Boolean = getEnvVar("DEVELOPMENT_MODE", "false").toBoolean(),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val scopeSyfotilgangskontroll: String = getEnvVar("SYFOTILGANGSKONTROLL_SCOPE"),
    val norskHelsenettEndpointURL: String = getEnvVar("HELSENETT_ENDPOINT_URL", "http://syfohelsenettproxy"),
    val helsenettproxyId: String = getEnvVar("HELSENETTPROXY_ID")

) : KafkaConfig

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val oidcWellKnownUri: String,
    val smregistreringBackendClientId: String,
    val smregistreringBackendClientSecret: String,
    val syfosmpapirregelClientId: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}
fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
