package no.nav.syfo.service

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.stream.Collectors
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
import no.nav.helse.sm2013.DynaSvarType
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.Ident
import no.nav.helse.sm2013.NavnType
import no.nav.helse.sm2013.TeleCom
import no.nav.helse.sm2013.URL
import no.nav.syfo.log
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Periode
import no.nav.syfo.model.RestrictionCode
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.pdl.model.PdlPerson

fun mapsmRegistreringManuelltTilFellesformat(
    smRegistreringManuell: SmRegistreringManuell,
    pdlPasient: PdlPerson,
    sykmelder: Sykmelder,
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
                genDate = datoOpprettet ?: LocalDateTime.of(smRegistreringManuell.perioder.first().fom, LocalTime.NOON)
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
                            givenName = sykmelder.fornavn
                            middleName = sykmelder.mellomnavn
                            familyName = sykmelder.etternavn
                            ident.addAll(
                                listOf(
                                    XMLIdent().apply {
                                        id = sykmelder.fnr
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
                            syketilfelleStartDato = tilSyketilfelleStartDato(smRegistreringManuell)
                            pasient = HelseOpplysningerArbeidsuforhet.Pasient().apply {
                                navn = NavnType().apply {
                                    fornavn = pdlPasient.navn.fornavn
                                    mellomnavn = pdlPasient.navn.mellomnavn
                                    etternavn = pdlPasient.navn.etternavn
                                }
                                fodselsnummer = Ident().apply {
                                    id = pdlPasient.fnr
                                    typeId = CV().apply {
                                        dn = "Fødselsnummer"
                                        s = "2.16.578.1.12.4.1.1.8116"
                                        v = "FNR"
                                    }
                                }
                            }
                            arbeidsgiver = tilArbeidsgiver(smRegistreringManuell.arbeidsgiver)
                            medisinskVurdering =
                                tilMedisinskVurdering(
                                    smRegistreringManuell.medisinskVurdering,
                                    smRegistreringManuell.skjermesForPasient
                                )
                            aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                                periode.addAll(tilPeriodeListe(smRegistreringManuell.perioder))
                            }
                            prognose = null
                            utdypendeOpplysninger = if (smRegistreringManuell.harUtdypendeOpplysninger) flaggScanHarUtdypendeOpplysninger() else null
                            tiltak = null
                            meldingTilNav = HelseOpplysningerArbeidsuforhet.MeldingTilNav().apply {
                                isBistandNAVUmiddelbart =
                                    smRegistreringManuell.meldingTilNAV?.bistandUmiddelbart ?: false
                                beskrivBistandNAV = smRegistreringManuell.meldingTilNAV?.beskrivBistand ?: ""
                            }
                            meldingTilArbeidsgiver = smRegistreringManuell.meldingTilArbeidsgiver
                            kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                                kontaktDato = smRegistreringManuell.kontaktMedPasient.kontaktDato
                                begrunnIkkeKontakt = smRegistreringManuell.kontaktMedPasient.begrunnelseIkkeKontakt
                                behandletDato = LocalDateTime.of(smRegistreringManuell.behandletDato, LocalTime.NOON)
                            }
                            behandler = tilBehandler(sykmelder)
                            avsenderSystem = HelseOpplysningerArbeidsuforhet.AvsenderSystem().apply {
                                systemNavn = "Papirsykmelding"
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

fun tilSyketilfelleStartDato(smRegistreringManuell: SmRegistreringManuell): LocalDate? {
    // Bruk innsendt syketilfelleStartDato, eller fall tilbake til dato fra perioder hvis ikke satt
    return smRegistreringManuell.syketilfelleStartDato
        ?: smRegistreringManuell.perioder
            .stream().map(Periode::fom).min(LocalDate::compareTo).get()
}

fun tilBehandler(sykmelder: Sykmelder): HelseOpplysningerArbeidsuforhet.Behandler =
    HelseOpplysningerArbeidsuforhet.Behandler().apply {
        navn = NavnType().apply {
            fornavn = sykmelder.fornavn
            mellomnavn = sykmelder.mellomnavn
            etternavn = sykmelder.etternavn
        }
        id.addAll(
            listOf(
                Ident().apply {
                    id = sykmelder.fnr
                    typeId = CV().apply {
                        dn = "Fødselsnummer"
                        s = "2.16.578.1.12.4.1.1.8327"
                        v = "FNR"
                    }
                },
                Ident().apply {
                    id = sykmelder.hprNummer
                    typeId = CV().apply {
                        dn = "HPR-nummer"
                        s = "2.16.578.1.12.4.1.1.8116"
                        v = "HPR"
                    }
                }
            )
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

fun flaggScanHarUtdypendeOpplysninger(): HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger {
    return HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger().apply {
        spmGruppe.add(HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger.SpmGruppe().apply {
            spmGruppeId = "6.1"
            spmGruppeTekst = "Utdypende opplysninger ved 7/8,17 og 39 uker"
            spmSvar.add(DynaSvarType().apply {
                spmTekst = "Utdypende opplysninger"
                restriksjon = DynaSvarType.Restriksjon().apply {
                    restriksjonskode.add(CS().apply {
                        dn = RestrictionCode.RESTRICTED_FOR_EMPLOYER.text
                        v = RestrictionCode.RESTRICTED_FOR_EMPLOYER.codeValue
                    })
                }
                spmId = "6.1.1"
                svarTekst = "Papirsykmeldingen inneholder utdypende opplysninger."
            })
        })
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
        aktivitetIkkeMulig = if (periode.aktivitetIkkeMulig != null) {
            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig().apply {
                medisinskeArsaker = if (periode.aktivitetIkkeMulig?.medisinskArsak != null) {
                    ArsakType().apply {
                        beskriv = periode.aktivitetIkkeMulig?.medisinskArsak?.beskrivelse
                        arsakskode.addAll(
                            periode.aktivitetIkkeMulig!!.medisinskArsak!!.arsak.stream().map {
                                CS().apply {
                                    v = it.codeValue
                                    dn = it.text
                                }
                            }.collect(Collectors.toList())
                        )
                    }
                } else {
                    null
                }
                arbeidsplassen = if (periode.aktivitetIkkeMulig?.arbeidsrelatertArsak != null) {
                    ArsakType().apply {
                        beskriv = periode.aktivitetIkkeMulig?.arbeidsrelatertArsak?.beskrivelse
                        arsakskode.addAll(
                            periode.aktivitetIkkeMulig!!.arbeidsrelatertArsak!!.arsak.stream().map {
                                CS().apply {
                                    v = it.codeValue
                                    dn = it.text
                                }
                            }.collect(Collectors.toList())
                        )
                    }
                } else {
                    null
                }
            }
        } else {
            null
        }
        avventendeSykmelding = if (periode.avventendeInnspillTilArbeidsgiver != null) {
            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AvventendeSykmelding().apply {
                innspillTilArbeidsgiver = periode.avventendeInnspillTilArbeidsgiver
            }
        } else {
            null
        }

        gradertSykmelding = if (periode.gradert != null) {
            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.GradertSykmelding().apply {
                sykmeldingsgrad = periode.gradert!!.grad
                isReisetilskudd = periode.gradert!!.reisetilskudd
            }
        } else {
            null
        }

        behandlingsdager = periode.behandlingsdager?.let { behandlingsdager ->
            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.Behandlingsdager().apply {
                antallBehandlingsdagerUke = behandlingsdager
            }
        }

        isReisetilskudd = periode.reisetilskudd
    }

fun tilArbeidsgiver(arbeidsgiver: Arbeidsgiver): HelseOpplysningerArbeidsuforhet.Arbeidsgiver =
    HelseOpplysningerArbeidsuforhet.Arbeidsgiver().apply {
        harArbeidsgiver =
            when {
                arbeidsgiver.harArbeidsgiver == HarArbeidsgiver.EN_ARBEIDSGIVER -> CS().apply {
                    dn = "Én arbeidsgiver"
                    v = "1"
                }
                arbeidsgiver.harArbeidsgiver == HarArbeidsgiver.FLERE_ARBEIDSGIVERE -> CS().apply {
                    dn = "Flere arbeidsgivere"
                    v = "2"
                }
                arbeidsgiver.harArbeidsgiver == HarArbeidsgiver.INGEN_ARBEIDSGIVER -> CS().apply {
                    dn = "Ingen arbeidsgiver"
                    v = "3"
                }
                else -> {
                    log.error("Arbeidsgiver type er ukjent, skal ikke kunne skje")
                    throw RuntimeException("Arbeidsgiver type er ukjent, skal ikke kunne skje")
                }
            }

        navnArbeidsgiver = arbeidsgiver.navn
        yrkesbetegnelse = arbeidsgiver.yrkesbetegnelse
        stillingsprosent = arbeidsgiver.stillingsprosent
    }

fun tilMedisinskVurdering(
    medisinskVurdering: MedisinskVurdering,
    skjermesForPasient: Boolean
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
        isSkjermesForPasient = skjermesForPasient
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
