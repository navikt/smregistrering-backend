package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "smregistrering-backend"),
    val serviceuserUsernamePath: String = getEnvVar("SERVICE_USER_USERNAME"),
    val serviceuserPasswordPath: String = getEnvVar("SERVICE_USER_PASSWORD"),
    val smregistreringbackendDBURL: String = getEnvVar("SMREGISTERINGB_BACKEND_DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "smregistrering-backend"),
    val smregistreringUrl: String = getEnvVar("SMREGISTERING_URL"),
    val securityTokenUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL", "http://security-token-service.default/rest/v1/sts/token"),
    val oppgavebehandlingUrl: String = getEnvVar("OPPGAVEBEHANDLING_URL"),
    val hentDokumentUrl: String = getEnvVar("HENT_DOKUMENT_URL"),
    val kuhrSarApiUrl: String = getEnvVar("KUHR_SAR_API_URL"),
    val kuhrSarApiScope: String = getEnvVar("KUHR_SAR_API_SCOPE"),
    val dokArkivUrl: String = getEnvVar("DOK_ARKIV_URL"),
    val dokArkivScope: String = getEnvVar("DOK_ARKIV_SCOPE"),
    val regelEndpointURL: String = getEnvVar("SYFOSMPAPIR_REGLER_ENDPOINT_URL"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val norskHelsenettEndpointURL: String = getEnvVar("HELSENETT_ENDPOINT_URL"),
    val helsenettproxyScope: String = getEnvVar("HELSENETT_SCOPE"),
    val safJournalpostGraphqlPath: String = getEnvVar("SAFJOURNALPOST_GRAPHQL_PATH"),
    val safScope: String = getEnvVar("SAF_SCOPE"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val syfosmpapirregelScope: String = getEnvVar("SYFOSMPAPIRREGLER_SCOPE"),
    val syfoTilgangsKontrollClientUrl: String = getEnvVar("SYFOTILGANGSKONTROLL_URL"),
    val syfoTilgangsKontrollScope: String = getEnvVar("SYFOTILGANGSKONTROLL_SCOPE"),
    val msGraphApiScope: String = getEnvVar("MS_GRAPH_API_SCOPE"),
    val msGraphApiUrl: String = getEnvVar("MS_GRAPH_API_URL"),
    val azureTokenEndpoint: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val azureAppClientId: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val azureAppClientSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val jwkKeysUrl: String = getEnvVar("AZURE_OPENID_CONFIG_JWKS_URI"),
    val jwtIssuer: String = getEnvVar("AZURE_OPENID_CONFIG_ISSUER"),
    val okSykmeldingTopic: String = "teamsykmelding.ok-sykmelding",
    val papirSmRegistreringTopic: String = "teamsykmelding.papir-sm-registering",
    val syfoserviceMqKafkaTopic: String = "teamsykmelding.syfoservice-mq",
)

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
