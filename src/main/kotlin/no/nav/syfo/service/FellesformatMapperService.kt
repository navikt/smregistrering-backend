package no.nav.syfo.service

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLCS
import no.nav.helse.msgHead.XMLCV
import no.nav.helse.msgHead.XMLDocument
import no.nav.helse.msgHead.XMLHealthcareProfessional
import no.nav.helse.msgHead.XMLIdent
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLMsgInfo
import no.nav.helse.msgHead.XMLOrganisation
import no.nav.helse.msgHead.XMLReceiver
import no.nav.helse.msgHead.XMLRefDoc
import no.nav.helse.msgHead.XMLSender
import no.nav.helse.sm2013.Address
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.Ident
import no.nav.helse.sm2013.NavnType
import no.nav.helse.sm2013.TeleCom
import no.nav.helse.sm2013.URL
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Periode
import no.nav.syfo.model.SmRegisteringManuellt

fun mapsmRegisteringManuelltTilFellesformat(
    smRegisteringManuellt: SmRegisteringManuellt,
    pasientFnr: String,
    sykmelderFnr: String,
    sykmeldingId: String,
    datoOpprettet: LocalDateTime?
): XMLEIFellesformat {
    return XMLEIFellesformat().apply {
        any.add(XMLMsgHead().apply {
            msgInfo = XMLMsgInfo().apply {
                type = XMLCS().apply {
                    dn = "Medisinsk vurdering av arbeidsmulighet ved sykdom, sykmelding"
                    v = "SYKMELD"
                }
                miGversion = "v1.2 2006-05-24"
                genDate = datoOpprettet ?: LocalDateTime.of(smRegisteringManuellt.perioder.first().fom, LocalTime.NOON)
                msgId = sykmeldingId
                ack = XMLCS().apply {
                    dn = "Ja"
                    v = "J"
                }
                sender = XMLSender().apply {
                    comMethod = XMLCS().apply {
                        dn = "EDI"
                        v = "EDI"
                    }
                    organisation = XMLOrganisation().apply {
                        healthcareProfessional = XMLHealthcareProfessional().apply {
                            givenName = ""
                            middleName = ""
                            familyName = ""
                            ident.addAll(
                                listOf(
                                    XMLIdent().apply {
                                        id = sykmelderFnr
                                        typeId = XMLCV().apply {
                                            dn = "Fødselsnummer"
                                            s = "2.16.578.1.12.4.1.1.8327"
                                            v = "FNR"
                                        }
                                    })
                            )
                        }
                    }
                }
                receiver = XMLReceiver().apply {
                    comMethod = XMLCS().apply {
                        dn = "EDI"
                        v = "EDI"
                    }
                    organisation = XMLOrganisation().apply {
                        organisationName = "NAV"
                        ident.addAll(
                            listOf(
                                XMLIdent().apply {
                                    id = "79768"
                                    typeId = XMLCV().apply {
                                        dn = "Identifikator fra Helsetjenesteenhetsregisteret (HER-id)"
                                        s = "2.16.578.1.12.4.1.1.9051"
                                        v = "HER"
                                    }
                                },
                                XMLIdent().apply {
                                    id = "889640782"
                                    typeId = XMLCV().apply {
                                        dn = "Organisasjonsnummeret i Enhetsregister (Brønøysund)"
                                        s = "2.16.578.1.12.4.1.1.9051"
                                        v = "ENH"
                                    }
                                })
                        )
                    }
                }
            }
            document.add(XMLDocument().apply {
                refDoc = XMLRefDoc().apply {
                    msgType = XMLCS().apply {
                        dn = "XML-instans"
                        v = "XML"
                    }
                    content = XMLRefDoc.Content().apply {
                        any.add(HelseOpplysningerArbeidsuforhet().apply {
                            syketilfelleStartDato = smRegisteringManuellt.perioder.first().fom
                            pasient = HelseOpplysningerArbeidsuforhet.Pasient().apply {
                                navn = NavnType().apply {
                                    fornavn = ""
                                    mellomnavn = ""
                                    etternavn = ""
                                }
                                fodselsnummer = Ident().apply {
                                    id = pasientFnr
                                    typeId = CV().apply {
                                        dn = "Fødselsnummer"
                                        s = "2.16.578.1.12.4.1.1.8116"
                                        v = "FNR"
                                    }
                                }
                            }
                            arbeidsgiver = tilArbeidsgiver()
                            medisinskVurdering =
                                tilMedisinskVurdering(smRegisteringManuellt.medisinskVurdering)
                            aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                                periode.addAll(tilPeriodeListe(smRegisteringManuellt.perioder))
                            }
                            prognose = null
                            utdypendeOpplysninger = tilUtdypendeOpplysninger()
                            tiltak = HelseOpplysningerArbeidsuforhet.Tiltak().apply {
                                tiltakArbeidsplassen = null
                                tiltakNAV = null
                                andreTiltak = null
                            }
                            meldingTilNav = null
                            meldingTilArbeidsgiver = null
                            kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                                kontaktDato = LocalDate.now()
                                begrunnIkkeKontakt = null
                                behandletDato = LocalDateTime.now()
                            }
                            behandler = tilBehandler(sykmelderFnr)
                            avsenderSystem = HelseOpplysningerArbeidsuforhet.AvsenderSystem().apply {
                                systemNavn = "ManuelltRegisterPapirsykmelding"
                                systemVersjon = "1"
                            }
                            strekkode = "123456789qwerty"
                        })
                    }
                }
            })
        })
    }
}

fun tilBehandler(sykmelderFnr: String): HelseOpplysningerArbeidsuforhet.Behandler =
    HelseOpplysningerArbeidsuforhet.Behandler().apply {
        navn = NavnType().apply {
            fornavn = ""
            mellomnavn = ""
            etternavn = ""
        }
        id.addAll(
            listOf(
                Ident().apply {
                    id = sykmelderFnr
                    typeId = CV().apply {
                        dn = "Fødselsnummer"
                        s = "2.16.578.1.12.4.1.1.8327"
                        v = "FNR"
                    }
                })
        )
        adresse = Address()
        kontaktInfo.add(TeleCom().apply {
            typeTelecom = CS().apply {
                v = "HP"
                dn = "Hovedtelefon"
            }
            teleAddress = URL().apply {
                v = "tel:55553336"
            }
        })
    }

fun tilUtdypendeOpplysninger(): HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger {
    return HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger().apply {
    }
}

fun tilPeriodeListe(perioder: List<Periode>): List<HelseOpplysningerArbeidsuforhet.Aktivitet.Periode> {
    return ArrayList<HelseOpplysningerArbeidsuforhet.Aktivitet.Periode>().apply {
        addAll(perioder.map {
            tilHelseOpplysningerArbeidsuforhetPeriode(it)
        })
    }
}

fun tilHelseOpplysningerArbeidsuforhetPeriode(periode: Periode): HelseOpplysningerArbeidsuforhet.Aktivitet.Periode =
    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
        periodeFOMDato = periode.fom
        periodeTOMDato = periode.tom
        aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig().apply {
            medisinskeArsaker = if (periode.aktivitetIkkeMulig?.medisinskArsak != null) {
                ArsakType().apply {
                    beskriv = periode.aktivitetIkkeMulig?.medisinskArsak?.beskrivelse
                    arsakskode.add(CS())
                }
            } else {
                null
            }
            arbeidsplassen = if (periode.aktivitetIkkeMulig?.arbeidsrelatertArsak != null) {
                ArsakType().apply {
                    beskriv = periode.aktivitetIkkeMulig?.arbeidsrelatertArsak?.beskrivelse
                    arsakskode.add(CS())
                }
            } else {
                null
            }
        }
        avventendeSykmelding = null
        gradertSykmelding = null
        behandlingsdager =
            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.Behandlingsdager().apply {
                antallBehandlingsdagerUke = periode.behandlingsdager ?: 0
            }
        isReisetilskudd = periode.reisetilskudd
    }

fun tilArbeidsgiver(): HelseOpplysningerArbeidsuforhet.Arbeidsgiver =
    HelseOpplysningerArbeidsuforhet.Arbeidsgiver().apply {
        harArbeidsgiver = CS().apply {
            dn = "Én arbeidsgiver"
            v = "1"
        }

        navnArbeidsgiver = ""
        yrkesbetegnelse = ""
        stillingsprosent = 100
    }

fun tilMedisinskVurdering(
    medisinskVurdering: MedisinskVurdering
): HelseOpplysningerArbeidsuforhet.MedisinskVurdering {

    val biDiagnoseListe: List<CV>? = medisinskVurdering.biDiagnoser.map {
        toMedisinskVurderingDiagnode(it)
    }

    return HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
        if (medisinskVurdering.hovedDiagnose != null) {
            hovedDiagnose = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose().apply {
                diagnosekode = toMedisinskVurderingDiagnode(medisinskVurdering.hovedDiagnose!!)
            }
        }
        if (biDiagnoseListe != null && biDiagnoseListe.isNotEmpty()) {
            biDiagnoser = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.BiDiagnoser().apply {
                diagnosekode.addAll(biDiagnoseListe)
            }
        }
        isSkjermesForPasient = false
        annenFraversArsak = medisinskVurdering.annenFraversArsak?.let {
            ArsakType().apply {
                arsakskode.add(CS())
                beskriv = medisinskVurdering.annenFraversArsak!!.beskrivelse
            }
        }
        isSvangerskap = medisinskVurdering.svangerskap
        isYrkesskade = medisinskVurdering.yrkesskade
        yrkesskadeDato = medisinskVurdering.yrkesskadeDato
    }
}

fun toMedisinskVurderingDiagnode(diagnose: Diagnose): CV =
    CV().apply {
        s = diagnose.system
        v = diagnose.kode
        dn = diagnose.tekst
    }
