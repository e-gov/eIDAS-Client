# Integraatori juhend

- [1. Paigaldamise eeldused](#eeldused)
- [2. eIDAS-Client'i Seadistamine](#konf_all)
  * [2.1. Konfiguratsioonifail](#konf)
  * [2.2. Paigaldamine war failina Tomcat rakendusserverisse](#war_deployment)
  * [2.3. Seadistusparameetrite loetelu](#parameetrid)
- [3. SAML võtmete genereerimine](#votmed)
- [4. Logimine](#logimine)
  * [4.1 Logimise vaikekonfiguratsioon](#logimine_naidis)
  * [4.2 Vaikekonfiguratsiooni seadistamine](#logimine_naidis)
  * [4.3 Välise log4j2.xml konfiguratsioonifaili kasutamine](#logimine_valine)
  * [4.4 Syslog serverisse edastamine](#syslog)
- [5. Monitoorimine](#heartbeat)
- [6. Hazelcast ja mitmes eksemplaris paigaldamine](#klasterdamine)
  * [6.1 Hazelcasti sisselülitamine](#hazelcast)
  * [6.2 Hazelcasti seadistamine](#hazelcast_seadistus)
  * [6.3 Andmete turvamine](#hazelcast_turva)
  * [6.4 Monitooring ja kasutusstatistika](#hazelcast_monitooring)

<a name="eeldused"></a>
## 1. Paigaldamise eeldused

eIDAS-Clienti paigaldamiseks on minimaalselt vajalik:
* JRE 8+
* Java rakendusserver (soovituslik Tomcat 8.x või uuem)


NB! Lisaks on nõutav ka ligipääs eIDAS Node rakendusele


<a name="konf_all"></a>
## 2. eIDAS-Client'i seadistamine
--------------------

Rakenduse seadistamine toimib läbi keskse Spring boot konfiguratsioonifaili - `application.properties` - mille asukoht tuleb rakendusele käivitamisel kaasa anda.

Juhul kui konfiguratsioonifaili asukohta ei täpsustata või fail pole ligipääsetav, rakenduvad vaikeseadistused. Vaikeseadistust on võimalik muuta, andes käivitamisel kaasa oma konfiguratisoonifaili koos soovitud parameetritega.

<a name="konf"></a>
### 2.1. Konfiguratsioonifail

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
### 2.2. Paigaldamine war failina Tomcat rakendusserverisse

1. Järgi [**juhiseid**](../README.md) ning ehita kokku eIDAS-Client `war` fail koos näidis konfiguratsioonifailiga.
2. Paigalda war fail rakendusserverisse. <br><br>NB! Soovituslik on paigaldada eIDAS-Client ainsa rakendusena rakendusserverisse (Tomcat puhul `ROOT` rakendusena)<br><br>
3. Anna rakendusserverile ette eIDAS-Client **konfiguratsioonifaili asukoht**. Selleks lisa `tomcat/bin` kausta `setenv.sh` fail, milles viidatud Spring boot konfiguratsioonifaili asukoht:
`export SPRING_CONFIG_LOCATION=/etc/eidas-client/application.properties`



<a name="parameetrid"></a>
### 2.3 Seadistusparameetrid

Tabel 2.3.1 - Teenusepakkuja metateabe seadistus

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


Tabel 2.3.2 - Konnektorteenuse metateabe küsimise seadistus

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `eidas.client.idpMetadataUrl`  | Jah | URL. Konnektorteenuse metateabe asukoht. https://eidastest.eesti.ee/EidasNode/ConnectorResponderMetadata |
| `eidas.client.idpMetadataSigningCertificateKeyId` | Ei | Konnektorteeenuse metateabe allkirjastamiseks kasutatud sertifikaadi alias võtmehoidlas. Vaikimisi alias: `metadata`. |

Tabel 2.3.3 - Saadetava AuthnRequesti ja SAML vastuse seadistus

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
| `eidas.client.allowedEidasAttributes` | Ei | Komaga eraldatud lubatud EidasAttribute väärtuste nimekiri. Vaikimisi väärtus on nimekiri kõigist võimalikest EidasAttribute enum väärtustest. |

Tabel 2.3.4 - turvaseadistused

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `security.allowedAuthenticationPort` | Ei | Olemasolu korral piirab ligipääsu autentimisotspunktidele (`/login` ja `/returnUrl`) vaid määratud pordi kaudu, misjuhul nimetatud otspunktide poole pöördumisel muude portide kaudu tagastatakse `403 Forbidden` ja [veakirjeldus JSON objektina](Service-API.md#veakasitlus). Lubatud väärtused: täisarv vahemikus 1 - 65535. |

Tabel 2.3.5 - heartbeat otspunkti seadistus

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `endpoints.heartbeat.timeout`  | Ei | Sõltuvate süsteemide kontrollimisel tehtava päringu puhul maksimaalne vastuse ooteag sekundites. Vaikimisi 3 sekundit. |

<a name="conf_hazelcast"></a>
Tabel 2.3.6 - Hazelcast seadistus

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `eidas.client.hazelcastEnabled`  | Ei | Hazelcasti toe aktiveerimine. |
| `eidas.client.hazelcastConfig`  | Ei <sup>1</sup> | <p>Viide Hazelcasti seadistusfailile. </p><p>Näide: `classpath:hazelcast.xml`, kui fail loetakse classpathi kaudu või `file:/etc/eidas-client/hazelcast.xml` kui loetakse otse failisüsteemist.</p> |
| `eidas.client.hazelcastSigningKey`  | Ei <sup>1</sup> | <p>HMAC võti base64 kodeeritud kujul (räsitabeli sisu allkirjastamiseks). Võtme pikkus sõltub allkirjastamise algoritmi valikust.</p> <p>Vaikimisi kasutatava HMAC512 puhul peab kasutama 512 bitist juhuarvu. </p><p>NB! Näide 512 bitise võtme genereerimisest openssl'ga: `openssl rand -base64 64`</p>|
| `eidas.client.hazelcastSigningAlgorithm`  | Ei | Allkirjastamisalgoritm (`HS512`, `HS384`, `HS256`). Vaikimisi `HS512`. |
| `eidas.client.hazelcastEncryptionKey`  | Ei <sup>1</sup> | <p>Krüpteerimisvõti base64 kodeeritud kujul (räsitabeli sisu krüpteerimisel kasutatav sümmeetriline võti). </p><p>Vaikimisi kasutatava `AES` algoritmi puhul peab võti olema alati 128 bitti</p><p>Näide 128 bitise võtme genereerimisest openssl'ga `openssl rand -base64 16` </p>|
| `eeidas.client.hazelcastEncryptionAlg`  | Ei | Krüpteerimisalgoritm vastavalt standardsele [Java Krüptograafiliste Algoritmide nimistule](https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#Cipher). Vaikimisi `AES`. |

<sup>1</sup> Kohustuslik juhul kui `eidas.client.hazelcastEnabled` on määratud.


Näide konfiguratsioonist:
```
eidas.client.hazelcastEnabled = true
eidas.client.hazelcastConfig = file:/etc/eidas-client/hazelcast.xml
eidas.client.hazelcastSigningKey=JgeUmXWHRs1FClKuStKRNWvfNWfFHWGSR8jgN8_xEoBSGnkiHHgEEHMttYmMtzy88rnlO6yfmQpSAJ0yNA9NWw
eidas.client.hazelcastSigningAlgorithm=HS512
eidas.client.hazelcastEncryptionKey=K7KVMOrgRj7Pw5GDHdXjKQ==
eidas.client.hazelcastEncryptionAlg=AES
```

Tabel 2.3.7 - Hazelcast kasutusstatistika otspunkt

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `endpoints.hazelcast.enabled`  | Ei | Võimalikud väärtused: `true`, `false`. Lülitab sisse `/hazelcast` otspunkti. Vaikimisi `false`. |


<a name="votmed"></a>
## 3. Test SAML võtmete genereerimine

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




<a name="logimine"></a>
## 4. Logimine
----------------

Logimiseks kasutatakse [Log4j2 raamistikku](https://logging.apache.org/log4j/2.x/index.html), mida on võimalik seadistada läbi [XML konfiguratsioonifaili](https://logging.apache.org/log4j/2.x/manual/configuration.html) (`log4j2.xml`).

<a name="logimine_naidis"></a>
### 4.1. Logimise vaikekonfiguratsioon

Rakenduses on kaasas [vaikeseadistusega konfiguratsioonifail](../eidas-client-webapp/src/main/resources/log4j2.xml), mis logib kohaliku failisüsteemi `/var/log/eidas` kausta failid mustriga `eIDAS-Client-%d{yyyy-MM-dd}`, näiteks `/var/log/eidas/eIDAS-Client-2019-08-06.log`. Rakendus hoiab viimase 7 päeva logisid pakkimata kujul. eIDAS-kliendi pakettides aset leidvate sündmuste logimise tasemeks `INFO`, kõik muud sündmused logitakse tasemel `WARN`.
Vaikeseadistuses väljastatakse logikirjed JSON kujul, iga logikirje lõpetab reavahetuse sümbol `\n`.

Tabel 4.1.1 - Logikirje struktuur

| Väli         | Kirjeldus | Alati olemas |
| :----------- | :-------- | :----------- |
| **date** | Sündmuse kuupäev ja kellaaeg ISO-8601 formaadis. Näide: `2018-09-13T10:06:50,682+0000` | Jah |
| **level** | Logisündmuse tase. Võimalikud väärtused (vähim tõsisest kõige tõsisemani): `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL` | Jah |
| **request** | Päringu meetod ja URL. Väärtustamata, kui logisündmus ei ole väljastatud päringu käigus. Näide: `GET http://eidas-client.arendus.kit:8080/login` | Ei |
| **requestId** | Päringu `X-Request-ID` päise väärtus, selle puudumisel päringut identifitseeriv juhugenereeritud 16 sümboliline tähtede-numbrite kombinatsioon. Väärtustamata, kui logisündmus ei ole väljastatud päringu käigus. | Ei |
| **sessionId** | Päringu `X-Correlation-ID` päise väärtus, selle puudumisel sessiooni ID-st genereeritud **sha256** räsi base64 kujul. Väärtustamata, kui logisündmus ei ole väljastatud päringu käigus. | Ei |
| **logger** | Logija nimi. | Jah |
| **thread** | Lõime nimi. | Jah |
| **message** | Logisõnum varjestatuna JSON-_escaping_'uga.| Jah |
| **throwable** | Vea _stack trace_ varjestatuna JSON-_escaping_'uga. | Ei |

Näide:

```
{"date":"2018-09-13T10:06:50,682+0000", "level":"INFO", "request":"GET http://eidas-client.arendus.kit:8080/login", "requestId":"0VVIBKN0GMZAKCVP", "sessionId":"LgoVYrdPv4PiHkRFGLfMD9h08dqpOC9NiVAQDL0hpGw=", "logger":"ee.ria.eidas.client.AuthInitiationService", "thread":"http-nio-8080-exec-1", "message":"SAML request ID: _8d4900cb8ae92034fa2cd89e6d8e8d89"}
```

<a name="logimine_seadistus"></a>
### 4.2 Vaikekonfiguratsiooni seadistamine

Vaikeseadistuses on võimalik juhtida logimise väljundit ning eIDAS-kliendi-spetsiifiliste logisündmuste logimise taset.

Tabel 4.2.1 - Vaikekonfiguratsioonifaili seadistatavad parameetrid

| Parameeter        | Kirjeldus | Vaikeväärtus |
| :---------------- | :---------- | :----------------|
| `eidas.client.log.pattern` | Logisündmuse muster. | `{"date":"%d{yyyy-MM-dd'T'HH:mm:ss,SSSZ}", "level":"%level"%notEmpty{, "request":"%X{request}"}%notEmpty{, "requestId":"%X{requestId}"}%notEmpty{, "sessionId":"%X{sessionId}"}, "logger":"%logger", "thread":"%thread", "message":"%enc{%msg}{JSON}"notEmpty{, "throwable":"%enc{%throwable}{JSON}"}}%n` |
| `eidas.client.log.level` | eIDAS-kliendi-spetsiifiliste sündmuste logimise tase. Üks järgnevatest väärtustest: `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE` | `info` |

Nende parameetrite vaikeväärtusi on võimalik muuta rakenduse käivitamisel etteantavate süsteemiparameetrite abil (vt. [Paigaldamine](Configuration.md#war_deployment) punkt 3), näiteks:

```
export JAVA_OPTS="-Deidas.client.log.pattern=%m%n -Deidas.client.log.level=debug"
```

Tabel 4.2.2 - Logimisel saadaolevad **MDC** (_Mapped Diagnostic Context_) atribuudid

| Atribuut          | Kirjeldus |
| :---------------- | :-------- |
| `request` | Päringu meetod ja URL. Väärtustamata, kui logisündmus ei ole väljastatud päringu käigus. Näide: `GET http://eidas-client.arendus.kit:8080/login` |
| `requestId` | Päringu `X-Request-ID` päise väärtus, selle puudumisel päringut identifitseeriv juhugenereeritud 16 sümboliline tähtede-numbrite kombinatsioon. Väärtustamata, kui logisündmus ei ole väljastatud päringu käigus. |
| `sessionId` | Päringu `X-Correlation-ID` päise väärtus, selle puudumisel sessiooni ID-st genereeritud **sha256** räsi base64 kujul. Väärtustamata, kui logisündmus ei ole väljastatud päringu käigus. |


<a name="logimine_valine"></a>
### 4.3 Välise log4j2.xml konfiguratsioonifaili kasutamine

Vajadusel on võimalik vaikekonfiguratsioonifaili asemel oma konfiguratsioonifaili kasutada. Selleks tuleb uue faili asukoht anda rakendusele käivitamisel süsteemiparameetrite abil (vt. [Paigaldamine](Configuration.md#war_deployment) punkt 3), näiteks:

```
export JAVA_OPTS="-Dlogging.config=/etc/eidas-client/log4j2.xml"
```

<a name="syslog"></a>
### 4.4 Syslog serveri kasutuselevõtt

Vaikekonfiguratsioonis sisalduv näidisseadistus võimaldab logi saata ka kesksesse syslog serverisse, mis toetab [RFC-5424 sõnumi formaati](https://tools.ietf.org/html/rfc5424.html#section-6.2.1) ning TLS-TCP protokolli.

Syslog protokolli sõnumiformaati on kitsendatud selliselt, et `facility` kood oleks alati `local1(17)` ja syslog prioriteet vea korral `error(3)` ja muudel juhtudel `notice(5)`.

NB! Logide kesksesse syslog serverisse saatmine ei ole vaikimisi sisselülitatud ning vajab lisaseadistust vastavalt konkreetse keskse syslog serveri parameetritele (serveri asukoht, port ja TLS kanali võtmed tuleb seadistada otse `log4j2.xml` failis.

<a name="heartbeat"></a>
## 5. Monitoorimine - rakenduse oleku pärimine

Rakenduse oleku info on kättesaadav otspunktilt **/heartbeat** või **/heartbeat.json**.

Rakenduse oleku info kuvamiseks kasutatakse Spring Boot Actuator raamistikku. Vaikeseadistuses on kõik otspunktid, välja arvatud **/heartbeat** otspunkt, välja lülitatud.

Lisaotspunkte on võimalik vajadusel seadistada vastavalt juhendile: <https://docs.spring.io/spring-boot/docs/1.5.10.RELEASE/reference/html/production-ready-endpoints.html> (NB! rakendust war failina eraldiseisvasse Tomcat rakendusserverisse paigaldades on otspunktide seadistus piiratud lisa otspunktide sisse- ja väljalülitamisega).



<a name="klasterdamine"></a>
## 6. Hazelcast - mitmes eksemplaris paigaldamine

eIDAS-Client peab SAML vastuse korrektsuse väljaselgitamiseks pidama arvet väljasaadetud SAML päringute kohta. Vaikimisi hoitakse väljastatud ja vastuseta päringute infot serveri mälus, mis tähendab, et klasterdamisel peab vastus tulema alati samasse õlga, kus päring väljastati. Alternatiiv on kasutada Hazelcasti klastris olevat räsitabelit päringuinfo jagamiseks eIDAS-Clienti eksemplaride vahel.

<a name="hazelcast"></a>
### 6.1 Hazelcasti sisselülitamine

Hazelcast käivitatakse koos eIDAS-Client rakenduse osana. Hazelcast käivitakse vaid juhul kui seadistusfailis on toodud Hazelcasti xml seadistusfaili asukoht (vt. [seadistusparaameetreid](#conf_hazelcast)).

<a name="hazelcast_seadistus"></a>
### 6.2 Hazelcasti seadistamine

Hazelcast seadistatakse deklaratiivselt, kasutades xml seadistusfaili. Loe Hazelcasti seadistamise detailide kohta rohkem [siit](https://docs.hazelcast.org/docs/3.11/manual/html-single/index.html#configuring-declaratively).


Näide minimaalsest konfiguratsioonist, mis kasutab TCP-IP tuvastamismehanismi:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<hazelcast xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xmlns="http://www.hazelcast.com/schema/config"
           xsi:schemaLocation="http://www.hazelcast.com/schema/config
                               https://hazelcast.com/schema/config/hazelcast-config-3.7.xsd">
    <group>
        <name>eidas-client-cluster</name>
    </group>
    <network>
        <port auto-increment="false">5702</port>
        <join>
            <multicast enabled="false"></multicast>
            <tcp-ip enabled="true">
				<member>xxx.xxx.xxx.xxx:5702</member>
                <member>yyy.yyy.yyy.yyy:5702</member>
                <member>zzz.zzz.zzz.zzz:5702</member>
			</tcp-ip>
        </join>
    </network>
</hazelcast>
```

<a name="hazelcast_turva"></a>
### 6.3 Andmete turvamine

eIDAS-Client krüpteerib sümmeetrilise võtmega (vaikimisi AES algoritmiga) ja allkirjastab andmed (vaikimisi HMAC512 algoritmiga) enne jagatud räsitabelisse salvestamist. Andmete küsimisel Hazelcastist verifitseeritakse allkiri ning alles seejärel dekrüpteeritakse.

Algoritmide seadistamise osas vt. [seadistusparaameetreid](#conf_hazelcast).

<a name="hazelcast_monitooring"></a>
### 6.4 Monitooring ja kasutusstatisika

Hazelcasti monitooringuks on võimalik kasutada Hazelcasti enda [health otspunkti](https://docs.hazelcast.org/docs/3.11/manual/html-single/index.html#health-check) (vaikimisi väljalülitatud).

Lisaks on võimalik detailsemat infot saada [diagnostika logist](https://docs.hazelcast.org/docs/3.11/manual/html-single/index.html#diagnostics) ja kontrollida klastrit detailsemalt, kui sisse lülitada [JMX pordi kasutus](https://docs.hazelcast.org/docs/3.11/manual/html-single/index.html#monitoring-with-jmx).

eIDAS-Client'i pakub ka `/hazelcast` otspunkti (vaikimimsi välja lülitatud) minimaalse kasutusstatistikaga.


