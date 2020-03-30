package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.mq.MqConfig

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
    val sm2013AutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-smpapir-automatiskBehandling"),
    val aktoerregisterV1Url: String = getEnvVar("AKTOR_REGISTER_V1_URL"),
    override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
    override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
    override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
    override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME"),
    val mqUsernamePath: String = getEnvVar("MQ_USERNAME"),
    val mqPasswordPath: String = getEnvVar("MQ_PASSWORD"),
    val syfoserviceQueueName: String = getEnvVar("MQ_SYFOSERVICE_QUEUE_NAME"),
    val kuhrSarApiUrl: String = getEnvVar("KUHR_SAR_API_URL", "http://kuhr-sar-api"),
    val dokArkivUrl: String = getEnvVar("DOK_ARKIV_URL")
) : MqConfig, KafkaConfig

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val mqUsername: String,
    val mqPassword: String,
    val oidcWellKnownUri: String,
    val smregistreringBackendClientId: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}
fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
