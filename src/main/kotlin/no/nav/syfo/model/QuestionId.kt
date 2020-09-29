package no.nav.syfo.model

enum class QuestionId(val spmId: String, val spmTekst: String) {
    ID_6_1_1("6.1.1", "Er det sykdommen, utredningen og/eller behandlingen som hindrer økt aktivitet? Beskriv."),
    ID_6_1_2("6.1.2", "Har behandlingen frem til nå bedret arbeidsevnen?"),
    ID_6_1_3("6.1.3", "Hva er videre plan for behandling?"),
    ID_6_1_4("6.1.4", "Er det arbeidsforholdet som hindrer (økt) aktivitet? Beskriv."),
    ID_6_1_5("6.1.5", "Er det andre forhold som hindrer (økt) aktivitet?"),
    ID_6_2_1("6.2.1", "Beskriv kort sykehistorie, symptomer og funn i dagens situasjon."),
    ID_6_2_2("6.2.2", "Hvordan påvirker sykdommen arbeidsevnen?"),
    ID_6_2_3("6.2.3", "Har behandlingen frem til nå bedret arbeidsevnen?"),
    ID_6_2_4("6.2.4", "Beskriv pågående og planlagt henvisning,utredning og/eller behandling."),
    ID_6_3_1("6.3.1", "Beskriv kort sykehistorie, symptomer og funn i dagens situasjon"),
    ID_6_3_2("6.3.2", "Beskriv pågående og planlagt henvisning, utredning og/eller behandling. Lar dette seg kombinere med delvis arbeid?"),
    ID_6_4_1("6.4.1", "Beskriv kort sykehistorie, symptomer og funn i dagens situasjon"),
    ID_6_4_2("6.4.2", "Beskriv pågående og planlagt henvisning, utredning og/eller behandling"),
    ID_6_4_3("6.4.3", "Hva mener du skal til for at pasienten kan komme tilbake i eget eller annet arbeid?"),
    ID_6_5_1("6.5.1", "Beskriv kort sykehistorie, symptomer og funn i dagens situasjon."),
    ID_6_5_2("6.5.2", "Hvordan påvirker dette funksjons-/arbeidsevnen?"),
    ID_6_5_3("6.5.3", "Beskriv pågående og planlagt henvisning, utredning og/eller medisinsk behandling"),
    ID_6_5_4("6.5.4", "Kan arbeidsevnen bedres gjennom medisinsk behandling og/eller arbeidsrelatert aktivitet? I så fall hvordan? Angi tidsperspektiv"),
    ID_6_6_1("6.6.1", "Hva antar du at pasienten kan utføre av eget arbeid/arbeidsoppgaver i dag eller i nær framtid?"),
    ID_6_6_2("6.6.2", "Hvis pasienten ikke kan gå tilbake til eget arbeid, hva antar du at pasienten kan utføre av annet arbeid/arbeidsoppgaver?"),
    ID_6_6_3("6.6.3", "Hvilken betydning har denne sykdommen for den nedsatte arbeidsevnen?"),
}

enum class RestrictionCode(override val codeValue: String, override val text: String, override val oid: String = "2.16.578.1.12.4.1.1.8134") : Kodeverk {
    RESTRICTED_FOR_EMPLOYER("A", "Informasjonen skal ikke vises arbeidsgiver"),
    RESTRICTED_FOR_PATIENT("P", "Informasjonen skal ikke vises pasient"),
    RESTRICTED_FOR_NAV("N", "Informasjonen skal ikke vises NAV")
}

interface Kodeverk {
    val codeValue: String
    val text: String
    val oid: String
}
