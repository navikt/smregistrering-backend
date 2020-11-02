package no.nav.syfo.pdl.client.model

data class PdlResponse(
    val hentIdenter: Identliste?,
    val hentPerson: HentPerson?
)

data class HentPerson(
    val navn: List<Navn>?
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)

data class Identliste(
    val identer: List<IdentInformasjon>
)

data class IdentInformasjon(
    val ident: String,
    val historisk: Boolean,
    val gruppe: String
)

data class ResponseError(
    val message: String?,
    val locations: List<ErrorLocation>?,
    val path: List<String>?,
    val extensions: ErrorExtension?
)

data class ErrorLocation(
    val line: String?,
    val column: String?
)

data class ErrorExtension(
    val code: String?,
    val classification: String?
)
