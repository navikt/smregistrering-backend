package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "smregistrering-backend"),
    val smregistreringUrl: String = getEnvVar("SMREGISTERING_URL"),
    val oppgavebehandlingUrl: String = getEnvVar("OPPGAVE_URL"),
    val oppgaveScope: String = getEnvVar("OPPGAVE_SCOPE"),
    val safV1Url: String = getEnvVar("SAF_URL"),
    val safScope: String = getEnvVar("SAF_SCOPE"),
    val dokArkivUrl: String = getEnvVar("DOK_ARKIV_URL"),
    val dokArkivScope: String = getEnvVar("DOK_ARKIV_SCOPE"),
    val regelEndpointURL: String = getEnvVar("SYFOSMPAPIRREGLER_URL", "http://syfosmpapirregler"),
    val syfosmpapirregelScope: String = getEnvVar("SYFOSMPAPIRREGLER_SCOPE"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val norskHelsenettEndpointURL: String = getEnvVar("HELSENETT_ENDPOINT_URL"),
    val helsenettproxyScope: String = getEnvVar("HELSENETT_SCOPE"),
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
    val syfoSmregisterEndpointURL: String = getEnvVar("SYFOSMREGISTER_ENDPOINT_URL", "http://syfosmregister"),
    val syfoSmregisterScope: String = getEnvVar("SYFOSMREGISTER_SCOPE"),
    val databaseUsername: String = getEnvVar("NAIS_DATABASE_USERNAME"),
    val databasePassword: String = getEnvVar("NAIS_DATABASE_PASSWORD"),
    val dbHost: String = getEnvVar("NAIS_DATABASE_HOST"),
    val dbPort: String = getEnvVar("NAIS_DATABASE_PORT"),
    val dbName: String = getEnvVar("NAIS_DATABASE_DATABASE"),
    val smtssApiUrl: String = "http://smtss",
    val smtssApiScope: String = getEnvVar("SMTSS_API_SCOPE"),
) {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$dbHost:$dbPort/$dbName"
    }
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
