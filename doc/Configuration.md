# Integraatori juhend
## Paigaldamine war failina

**Konfiguratsioonifaili asukoht** - Veebirakenduse paigaldamisel traditisiooonilisse Java Servlet tuge pakkuvasse veebiserverisse `war` failina, tuleb rakenduse keskse konfiguratsioonifaili asukoht ette anda keskkonnamuutujana - `SPRING_CONFIG_LOCATION`.

Näide: war failina paigaldades Tomcat veebiserverisse lisa `tomcat/bin` kausta `setenv.sh` fail järgmise sisuga:
`export SPRING_CONFIG_LOCATION=/etc/eidas-client/application.properties`

## Testvõtmete genereerimine

Veebirakendus vajab oma tööks mitut komplekti avaliku- ja privaatvõtme paari ja lisaks ka konnektorteenuse metateabe avaliku võtit ehk nö usaldusankrut. Võtmepaare hoitakse samas `jks` võtmehoidlas (vaikimisi failinimi on `samlKeystore.jks`, kui pole konfiguratsioonifailis sätestatud teisiti). Võtmepaarile viitakse konfiguratsioonis läbi määratud aliase.

Näide vajalike võtmete genererimisest Java `keytool` abil:

**Võtmehoidla loomine ja usaldusankru võtmepaar (metateabe allkirjastamine)**
`keytool -genkeypair -keyalg EC -keystore $keystoreFileName -keysize 384 -alias metadata -dname "CN=SP-metada-signing, OU=test, O=test, C=EE" -validity 730 -storepass $password -keypass $pass`

**Autentimispäringu allkirjastamise võtmepaar**
`keytool -genkeypair -keyalg EC -keystore $keystoreFileName -keysize 384 -alias requestsigning -dname "CN=SP-auth-request-signing, OU=test, O=test, C=EE" -validity 730 -storepass $password -keypass $password`

**Autentimisvastuse krüpteerimise võtmepaar**
`keytool -genkeypair -keyalg RSA -keystore $keystoreFileName -keysize 4096 -alias responseencryption -dname "CN=SP-response-encryption, OU=test, O=test, C=EE" -validity 730 -storepass $password -keypass $password`

**Konnektorteenuse usaldusankru import**
`keytool -importcert -keystore $keystoreFileName -storepass $password -file scripts/ee_eidasnode.pem -alias idpmetadata -noprompt`


## Seadistamine
--------------------

Rakenduse seadistamine toimib läbi keskse Spring boot konfiguratsioonifaili - `application.properties` - mille asukoht tuleb rakendusele käivitamisel kaasa anda.

Juhul kui konfiguratsioonifaili asukohta ei täpsustata või fail pole ligipääsetav, rakenduvad vaikeseadistused. Vaikeseadistust on võimalik muuta, andes käivitamisel kaasa oma konfiguratisoonifaili koos soovitud parameetritega.

### Logimine

Vaikimisi logitakse `INFO` tasemel rakenduse konsooli.

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `logging.level.ee.ria.eidas.client` | Ei | Logimise tase. Üks järgnevatest väärtustest: `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE` |


### Seadistusparameetrid

Tabel 3.1 - Teenusepakkuja metateabe seadistus

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


Tabel 3.2 - Konnektorteenuse metateabe küsimise seadistus

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `eidas.client.idpMetadataUrl`  | Jah | URL. Konnektorteenuse metateabe asukoht. https://eidastest.eesti.ee/EidasNode/ConnectorResponderMetadata |
| `eidas.client.idpMetadataSigningCertificateKeyId` | Ei | Konnektorteeenuse metateabe allkirjastamiseks kasutatud sertifikaadi alias võtmehoidlas. Vaikimisi alias: `metadata`. |

Tabel 3.3 - Saadetava AuthnRequesti ja SAML vastuse seadistus

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


