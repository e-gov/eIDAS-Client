
## Teenuse ülesehitus
-----------

Eidas klient veebiteenus paketeeritakse war failina.


<img src='img/EidasClient.png'></img>

Joonis 2.

## Komponendid


### eIDAS-klient teek

Eidas kliendi teenuskiht toetub [OpenSAML 3](https://wiki.shibboleth.net/confluence/display/OS30/Home) ja [Spring Boot](https://projects.spring.io/spring-boot/) alusteekidele.

### Konfiguratsioon

Rakenduse seadistamine toimib läbi keskse konfiguratsioonfaili.

### Logimine

Logimine teostatakse läbi SLF4J kasutades Logback raamistikku. Seetõttu on võimalik logimist seadistada läbi tavalise Logback konfiguratsioonifaili. Vaikimisi logitakse INFO tasemel ja kõik logid juhitakse süsteemi konsooli. Täiendavaid väljundkanaleid on võimalik vajadusel seadistada.

## Pakutavad liidesed

Ülepiirilise isikutuvastuse päringu algatamiseks ja tagasituleva vastuse vajalike otspunktide loetelu on toodud Tabelis 1. Otspunktide täpsem kirjeldus toodud jaotises "Liidese spetsifikatsioon".

| Otspunkt        | Toetatud meetodid | Selgitus  |
| ------------- | :------: | :-------------|
| `/login`  | POST | POST meetodil pöördudes algatatakse isikutuvastusprotsess valitud riigi eIDAS sõlmpunkti vastu. |
| `/returnUrl`  | POST |	Isikutuvastuse tulemuse vastuvõtt. Isikuandmete või vea kuvamine vastavalt parameetritele. |
| `/metadata`  | GET |	SAML 2.0 standardijärgne metateabe otspunkt. Vajalik eIDAS konnektorteenuse ja kliendi vahelise usalduse loomiseks. |
Tabel 1.

## Nõutud liidesed

Toimimiseks vajab eIDAS klient eIDAS konnektorteenust ning genereeritud võtmeid koos seadistusega.

| Komponent        | Selgitus |
| ------------- | :----- |
| `eIDAS konnektorteenuse metateave` | eIDAS klient vajab ligipääsu eIDAS konnektorteenuse SAML 2.0 metadata otspunktile |
| `eIDAS konnektorteenuse autentimisteenus` | eIDAS konnektorteenuse isikutuvastuse päringu vastuvõtu otspunkt (eIDAS konnektorteenuse metadata vastuses viidatud kui `SingleSignOnService`) |
| `Võtmehoidla` | SAML vastuste allkirjastamiseks vajalikke võtmeid hoitakse võtmehoidlates (pkcs12, jks). |
| `Konfiguratsioon` | Teenuse juhtimine ja seadistus toimib läbi keskse konfiguratsioonifaili. |
Tabel 2.