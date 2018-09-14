# Integraatori juhend

## 1. Paigaldamise eeldused

eIDAS-Clienti paigaldamiseks on minimaalselt vajalik:
* JRE 8+
* Java rakendusserver (soovituslik Tomcat 8.x või uuem)


NB! Lisaks on nõutav ka ligipääs eIDAS Node rakendusele


## 2. Testvõtmete genereerimine

Veebirakendus vajab oma tööks mitut komplekti avaliku- ja privaatvõtme paari ja lisaks ka konnektorteenuse metateabe avaliku võtit ehk nö usaldusankrut. Võtmepaare hoitakse samas `jks` võtmehoidlas (vaikimisi failinimi on `samlKeystore.jks`, kui pole konfiguratsioonifailis sätestatud teisiti). Võtmepaarile viitakse konfiguratsioonis läbi määratud aliase.

Näide vajalike võtmete genererimisest Java `keytool` abil:

**1. Võtmehoidla loomine ja usaldusankru võtmepaar (eIDAS-Client metateabe allkirjastamiseks)**
`keytool -genkeypair -keyalg EC -keystore $keystoreFileName -keysize 384 -alias metadata -dname "CN=SP-metada-signing, OU=test, O=test, C=EE" -validity 730 -storepass $password -keypass $pass`

**2. Autentimispäringu allkirjastamise võtmepaar**
`keytool -genkeypair -keyalg EC -keystore $keystoreFileName -keysize 384 -alias requestsigning -dname "CN=SP-auth-request-signing, OU=test, O=test, C=EE" -validity 730 -storepass $password -keypass $password`

**3. Autentimisvastuse krüpteerimise võtmepaar**
`keytool -genkeypair -keyalg RSA -keystore $keystoreFileName -keysize 4096 -alias responseencryption -dname "CN=SP-response-encryption, OU=test, O=test, C=EE" -validity 730 -storepass $password -keypass $password`

**4. Konnektorteenuse usaldusankru import**
`keytool -importcert -keystore $keystoreFileName -storepass $password -file scripts/ee_eidasnode.pem -alias idpmetadata -noprompt`

## 3. Konfiguratsioonifail

eIDAS-Client rakendus vajab oma tööks konfiguratsioonifaili, mis sätestab kasutatavate SAML võtmete asukoha, kliendi nime  ja olulised URLid, mis on vajalikud SAML päringute moodustamiseks ja töötluseks. Detailne konfiguratsiooniparameetrite kirjeldus on toodud alajaotuses [Seadistamine](#5.3-Seadistusparameetrid))

Järgnev on näidis minimaalsest vajaminevast konfiguratsioonifailist (viidetega eelnevas punktis genereeritud võtmetele):

```
# Keystore
eidas.client.keystore = file:/opt/tomcat/samlKeystore-test.jks
eidas.client.keystorePass = ...

# Key used for signing the SAML metadata
eidas.client.metadataSigningKeyId = metadatasigning
eidas.client.metadataSigningKeyPass = ...
eidas.client.metadataSignatureAlgorithm = http://www.w3.org/2007/05/xmldsig-more#sha256-rsa-MGF1

# Key used for signing the SAML AuthnRequest
eidas.client.requestSigningKeyId = requestsigning
eidas.client.requestSigningKeyPass = ...
eidas.client.requestSignatureAlgorithm = http://www.w3.org/2007/05/xmldsig-more#sha256-rsa-MGF1

# Key used to decrypt the SAML Assertion in response
eidas.client.responseDecryptionKeyId = responseencryption
eidas.client.responseDecryptionKeyPass = ...

# IDP metadata location
eidas.client.idpMetadataUrl = http://eidas-node.dev:8080/EidasNode/ConnectorResponderMetadata

eidas.client.providerName = EIDAS KLIENT DEMO
eidas.client.spEntityId = http://eidas-client.dev:8080/metadata
eidas.client.callbackUrl = https://eidas-client.dev/returnUrl

eidas.client.availableCountries = EE,CA,CD
```

<a name="war_deployment"></a>
## 4. Paigaldamine war failina Tomcat rakendusserverisse

1. Järgi [**juhiseid**](../README.md) ning ehita kokku eIDAS-Client `war` fail koos näidis konfiguratsioonifailiga.
2. Paigalda war fail rakendusserverisse. <br><br>NB! Soovituslik on paigaldada eIDAS-Client ainsa rakendusena rakendusserverisse (Tomcat puhul `ROOT` rakendusena)<br><br>
3. Anna rakendusserverile ette eIDAS-Client **konfiguratsioonifaili asukoht**. Selleks lisa `tomcat/bin` kausta `setenv.sh` fail, milles viidatud Spring boot konfiguratsioonifaili asukoht:
`export SPRING_CONFIG_LOCATION=/etc/eidas-client/application.properties`


## 5. Seadistamine
--------------------

Rakenduse seadistamine toimib läbi keskse Spring boot konfiguratsioonifaili - `application.properties` - mille asukoht tuleb rakendusele käivitamisel kaasa anda.

Juhul kui konfiguratsioonifaili asukohta ei täpsustata või fail pole ligipääsetav, rakenduvad vaikeseadistused. Vaikeseadistust on võimalik muuta, andes käivitamisel kaasa oma konfiguratisoonifaili koos soovitud parameetritega.

### 5.1 Rakenduse oleku pärimine

Rakenduse oleku info on kättesaadav otspunktilt **/heartbeat** või **/heaartbeat.json**.

Rakenduse oleku info kuvamiseks kasutatakse Spring Boot Actuator raamistikku. Vaikeseadistuses on kõik otspunktid, välja arvatud **/heartbeat** otspunkt, välja lülitatud.

Lisaotspunkte on võimalik vajadusel seadistada vastavalt juhendile: <https://docs.spring.io/spring-boot/docs/1.5.10.RELEASE/reference/html/production-ready-endpoints.html> (NB! rakendust war failina eraldiseisvasse Tomcat rakendusserverisse paigaldades on otspunktide seadistus piiratud lisa otspunktide sisse- ja väljalülitamisega).


### 5.2 Seadistusparameetrid

Tabel 5.1 - Teenusepakkuja metateabe seadistus

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `eidas.client.keystore` | Jah | Võtmehoidla asukoha kirjeldus. Näide: `classpath:samlKeystore.jks`, kui fail loetakse classpathi kaudu või `file:/etc/eidas-client/samlKeystore.jks` kui loetakse otse failisüsteemist. Võtmehoidla peab olema JKS tüüpi. |
| `eidas.client.keystorePass` | Jah | SAML võtmehoidla parool. |
| `eidas.client.metadataSigningKeyId` | Jah | SAML metateabe allkirjastamisvõtme alias. |
| `eidas.client.metadataSigningKeyPass` | Jah | SAML metateabe allkirjastamisvõtme parool. |
| `eidas.client.metadataSignatureAlgorithm` | Ei | Metateabe allkirja algoritm. Lubatud väärtused vastavalt. Vaikimisi `http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512`  |
| `eidas.client.responseDecryptionKeyId` | Jah | SAML autentimisvastuse dekrepteerimisvõtme alias. |
| `eidas.client.responseDecryptionKeyPass` | Jah | SAML autentimisvastuse dekrüpteerimisvõtme parool. |
| `eidas.client.spEntityId` | Jah | URL, mis viitab teenusepakkuja metateabele. `/md:EntityDescriptor/@entityID` väärtus metateabes. Näiteks: https://hostname:8889/metadata |
| `eidas.client.callbackUrl` | Jah | URL, mis viitab teenusepakkuja SAML`/md:EntityDescriptor/md:SPSSODescriptor/md:AssertionConsumerService/@Location` väärtus metateabes. |
| `eidas.client.metadataValidityInDays` | Ei | Konnektorteeenuse metateabe kehtivusaeg päevades. Vaikimisi 1 päev. |
| `eidas.client.spType` | Ei | Lubatud väärtused `public` ja `private`. EIDAS spetsiifiline parameeter metateabes `/md:EntityDescriptor/md:Extensions/eidas:SPType`. Vaikimisi `public`. |


Tabel 5.2 - Konnektorteenuse metateabe küsimise seadistus

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `eidas.client.idpMetadataUrl`  | Jah | URL. Konnektorteenuse metateabe asukoht. https://eidastest.eesti.ee/EidasNode/ConnectorResponderMetadata |
| `eidas.client.idpMetadataSigningCertificateKeyId` | Ei | Konnektorteeenuse metateabe allkirjastamiseks kasutatud sertifikaadi alias võtmehoidlas. Vaikimisi alias: `metadata`. |

Tabel 5.3 - Saadetava AuthnRequesti ja SAML vastuse seadistus

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `eidas.client.providerName` | Jah | Teenusepakkuja lühinimetus. `/saml2p:AuthnRequest/@ProviderName` väärtus. |
| `eidas.client.requestSigningKeyId` | Jah | SAML autentimispäringu allkirjastamisvõtme alias. |
| `eidas.client.requestSigningKeyPass` | Jah | SAML autentimispäringu allkirjastamisvõtme parool. |
| `eidas.client.acceptedClockSkew` | Ei | IDP ja SP süsteemide vaheline maksimaalselt aktsepteeritav kellaaegade erinevus sekundites. Vaikimisi 2. |
| `eidas.client.maximumAuthenticationLifetime` | Ei | Autentimispäringu eluiga sekundites. Vaikimisi 900. |
| `eidas.client.responseMessageLifeTime` | Ei | SAML vastuse eluiga sekundites. Vaikimisi 900. |
| `eidas.client.requestSignatureAlgorithm` | Ei | Autentimispäringu allkirja algoritm. Vaikimisi `http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512` |
| `eidas.client.availableCountries` | Ei | Lubatud riigikoodid. |
| `eidas.client.defaultLoa` | Ei | EIDAS tagatistase juhul kui kasutaja tagatistaseme ise määramata. Lubatud väärtused: 'LOW', 'SUBSTANTIAL', 'HIGH'. Vaikimisi 'SUBSTANTIAL'. |

Tabel 5.4 - heartbeat otspunkti seadistus

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `endpoints.heartbeat.timeout`  | Ei | Sõltuvate süsteemise kontrollimisel tehtava päringu puhul maksimaalne vastuse ooteag sekundites. Vaikimisi 3 sekundit. |


## 6. Logimine
----------------

Logimiseks kasutatakse [Log4j2 raamistikku](https://logging.apache.org/log4j/2.x/index.html), mida on võimalik seadistada läbi [XML konfiguratsioonifaili](https://logging.apache.org/log4j/2.x/manual/configuration.html) (`log4j2.xml`).

Rakenduses on kaasas [vaikeseadistusega konfiguratsioonifail](eidas-client-webapp/src/main/resources/log4j2.xml), mis määrab logimise vaikeväljundiks süsteemi konsooli ning eIDAS-kliendi pakettides aset leidvate sündmuste logimise tasemeks `INFO`. Kõik muud sündmused logitakse tasemel `WARN`.
Vaikeseadistuses on võimalik juhtida logimise väljundit ning eIDAS-kliendi-spetsiifiliste logisündmuste logimise taset.

Tabel 6.1 - Vaikekonfiguratsioonifaili seadistatavad parameetrid

| Parameeter        | Kirjeldus | Vaikeväärtus |
| :---------------- | :---------- | :----------------|
| `eidas.client.log.pattern` | Logisündmuse muster. | `{&quot;date&quot;:&quot;%d{yyyy-MM-dd'T'HH:mm:ss,SSSZ}&quot;, &quot;level&quot;:&quot;%level&quot;, &quot;request&quot;:&quot;%X{request}&quot;, &quot;requestId&quot;:&quot;%X{requestId}&quot;, &quot;sessionId&quot;:&quot;%X{sessionId}&quot;, &quot;logger&quot;:&quot;%logger&quot;, &quot;thread&quot;:&quot;%thread&quot;, &quot;msg&quot;:&quot;%m%throwable&quot;}%n` |
| `eidas.client.log.level` | eIDAS-kliendi-spetsiifiliste sündmuste logimise tase. Üks järgnevatest väärtustest: `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE` | `info` |

Nende parameetrite vaikeväärtusi on võimalik muuta rakenduse käivitamisel etteantavate süsteemiparameetrite abil (vt. [Paigaldamine](Configuration.md#war_deployment) punkt 3), näiteks:

```
export JAVA_OPTS="-Deidas.client.log.pattern=%m%n -Deidas.client.log.level=debug"
```

Vajadusel on võimalik vaikekonfiguratsioonifaili asemel oma konfiguratsioonifaili kasutada. Selleks tuleb uue faili asukoht anda rakendusele käivitamisel süsteemiparameetrite abil (vt. [Paigaldamine](Configuration.md#war_deployment) punkt 3), näiteks:

```
export JAVA_OPTS="-Dlogging.config=/etc/eidas-client/log4j2.xml"
```

