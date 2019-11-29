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
eidas.client.keystore-pass = ...

# Key used for signing the SAML metadata
eidas.client.metadata-signing-key-id = metadatasigning
eidas.client.metadata-signing-key-pass = ...
eidas.client.metadata-signature-algorithm = http://www.w3.org/2007/05/xmldsig-more#sha256-rsa-MGF1

# Key used for signing the SAML AuthnRequest
eidas.client.request-signing-key-id = requestsigning
eidas.client.request-signing-key-pass = ...
eidas.client.request-signature-algorithm = http://www.w3.org/2007/05/xmldsig-more#sha256-rsa-MGF1

# Key used to decrypt the SAML Assertion in response
eidas.client.response-decryption-key-id = responseencryption
eidas.client.response-decryption-key-pass = ...

# IDP metadata location
eidas.client.idp-metadata-url = http://eidas-node.dev:8080/EidasNode/ConnectorResponderMetadata

eidas.client.provider-name = EIDAS KLIENT DEMO
eidas.client.sp-entity-id = http://eidas-client.dev:8080/metadata
eidas.client.callback-url = https://eidas-client.dev/returnUrl

eidas.client.available-countries = EE,CA,CD
```

<a name="war_deployment"></a>
### 2.2. Paigaldamine war failina Tomcat rakendusserverisse

1. Järgi [**juhiseid**](../README.md) ning ehita kokku eIDAS-Client `war` fail koos näidis konfiguratsioonifailiga.
2. Paigalda war fail rakendusserverisse. <br><br>NB! Soovituslik on paigaldada eIDAS-Client ainsa rakendusena rakendusserverisse (Tomcat puhul `ROOT` rakendusena)<br><br>
3. Anna rakendusserverile ette eIDAS-Client **konfiguratsioonifaili asukoht**. Selleks lisa `tomcat/bin` kausta `setenv.sh` fail, milles viidatud Spring boot konfiguratsioonifaili asukoht:
`export SPRING_CONFIG_ADDITIONAL_LOCATION=/etc/eidas-client/application.properties`



<a name="parameetrid"></a>
### 2.3 Seadistusparameetrid

Tabel 2.3.1 - Teenusepakkuja metateabe seadistus

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `eidas.client.keystore` | Jah | Võtmehoidla asukoha kirjeldus. Näide: `classpath:samlKeystore.jks`, kui fail loetakse classpathi kaudu või `file:/etc/eidas-client/samlKeystore.jks` kui loetakse otse failisüsteemist. Võtmehoidla peab olema JKS tüüpi. |
| `eidas.client.keystore-pass` | Jah | SAML võtmehoidla parool. |
| `eidas.client.metadata-signing-key-id` | Jah | SAML metateabe allkirjastamisvõtme alias. |
| `eidas.client.metadata-signing-key-pass` | Jah | SAML metateabe allkirjastamisvõtme parool. |
| `eidas.client.metadata-signature-algorithm` | Ei | Metateabe allkirja algoritm. Lubatud väärtused vastavalt. Vaikimisi `http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512`  |
| `eidas.client.response-decryption-key-id` | Jah | SAML autentimisvastuse dekrepteerimisvõtme alias. |
| `eidas.client.response-decryption-key-pass` | Jah | SAML autentimisvastuse dekrüpteerimisvõtme parool. |
| `eidas.client.sp-entity-id` | Jah | URL, mis viitab teenusepakkuja metateabele. `/md:EntityDescriptor/@entityID` väärtus metateabes. Näiteks: https://hostname:8889/metadata |
| `eidas.client.callback-url` | Jah | URL, mis viitab teenusepakkuja SAML`/md:EntityDescriptor/md:SPSSODescriptor/md:AssertionConsumerService/@Location` väärtus metateabes. |
| `eidas.client.metadata-validity-in-days` | Ei | Konnektorteeenuse metateabe kehtivusaeg päevades. Vaikimisi 1 päev. |
| `eidas.client.sp-type` | Ei | Lubatud väärtused `public` ja `private`. EIDAS spetsiifiline parameeter metateabes `/md:EntityDescriptor/md:Extensions/eidas:SPType`. Vaikimisi `public`. |


Tabel 2.3.2 - Konnektorteenuse metateabe küsimise seadistus

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `eidas.client.idp-metadata-url`  | Jah | URL. Konnektorteenuse metateabe asukoht. https://eidastest.eesti.ee/EidasNode/ConnectorResponderMetadata |
| `eidas.client.idp-metadata-signing-certificate-key-id` | Ei | Konnektorteeenuse metateabe allkirjastamiseks kasutatud sertifikaadi alias võtmehoidlas. Vaikimisi alias: `metadata`. |

Tabel 2.3.3 - Saadetava AuthnRequesti ja SAML vastuse seadistus

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `eidas.client.provider-name` | Jah | Teenusepakkuja lühinimetus. `/saml2p:AuthnRequest/@ProviderName` väärtus. |
| `eidas.client.request-signing-key-id` | Jah | SAML autentimispäringu allkirjastamisvõtme alias. |
| `eidas.client.request-signing-key-pass` | Jah | SAML autentimispäringu allkirjastamisvõtme parool. |
| `eidas.client.accepted-clock-skew` | Ei | IDP ja SP süsteemide vaheline maksimaalselt aktsepteeritav kellaaegade erinevus sekundites. Vaikimisi 2. |
| `eidas.client.maximum-authentication-lifetime` | Ei | Autentimispäringu eluiga sekundites. Vaikimisi 900. |
| `eidas.client.response-message-lifetime` | Ei | SAML vastuse eluiga sekundites. Vaikimisi 900. |
| `eidas.client.request-signature-algorithm` | Ei | Autentimispäringu allkirja algoritm. Vaikimisi `http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512` |
| `eidas.client.available-countries` | Ei | Lubatud riigikoodid. |
| `eidas.client.default-loa` | Ei | EIDAS tagatistase juhul kui kasutaja tagatistaseme ise määramata. Lubatud väärtused: 'LOW', 'SUBSTANTIAL', 'HIGH'. Vaikimisi 'SUBSTANTIAL'. |
| `eidas.client.allowed-eidas-attributes` | Ei | Komaga eraldatud lubatud EidasAttribute väärtuste nimekiri. Vaikimisi väärtus on nimekiri kõigist võimalikest EidasAttribute enum väärtustest. |

Tabel 2.3.4 - turvaseadistused

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `security.allowed-authentication-port` | Ei | Olemasolu korral piirab ligipääsu autentimisotspunktidele (`/login` ja `/returnUrl`) vaid määratud pordi kaudu, misjuhul nimetatud otspunktide poole pöördumisel muude portide kaudu tagastatakse `403 Forbidden` ja [veakirjeldus JSON objektina](Service-API.md#veakasitlus). Lubatud väärtused: täisarv vahemikus 1 - 65535. |
| `security.disabled-http-methods` | Ei | Komaga eraldatud nimekiri HTTP meetoditest. Olemasolu korral piirab ligipääsu HTTP meetoditele (nimekirjas toodud meetodi kasutuse korral tagastatakse HTTP 405). Kui määramata, siis vaikimisi keelatud HTTP meetodite nimekirja kuuluvad: HEAD, PUT, PATCH, DELETE, OPTIONS, TRACE. Lubatud väärtused: GET, POST, HEAD, PUT, PATCH, DELETE, OPTIONS, TRACE |

Tabel 2.3.5 - heartbeat otspunkti seadistus

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `management.endpoint.heartbeat.timeout`  | Ei | Sõltuvate süsteemide kontrollimisel tehtava päringu puhul maksimaalne vastuse ooteag sekundites. Vaikimisi 3 sekundit. |

<a name="conf_hazelcast"></a>
Tabel 2.3.6 - Hazelcast seadistus

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `eidas.client.hazelcast-enabled`  | Ei | Hazelcasti toe aktiveerimine. |
| `eidas.client.hazelcast-config`  | Ei <sup>1</sup> | <p>Viide Hazelcasti seadistusfailile. </p><p>Näide: `classpath:hazelcast.xml`, kui fail loetakse classpathi kaudu või `file:/etc/eidas-client/hazelcast.xml` kui loetakse otse failisüsteemist.</p> |
| `eidas.client.hazelcast-signing-key`  | Ei <sup>1</sup> | <p>HMAC võti base64 kodeeritud kujul (räsitabeli sisu allkirjastamiseks). Võtme pikkus sõltub allkirjastamise algoritmi valikust.</p> <p>Vaikimisi kasutatava HMAC512 puhul peab kasutama 512 bitist juhuarvu. </p><p>NB! Näide 512 bitise võtme genereerimisest openssl'ga: `openssl rand -base64 64`</p>|
| `eidas.client.hazelcast-signing-algorithm`  | Ei | Allkirjastamisalgoritm (`HS512`, `HS384`, `HS256`). Vaikimisi `HS512`. |
| `eidas.client.hazelcast-encryption-key`  | Ei <sup>1</sup> | <p>Krüpteerimisvõti base64 kodeeritud kujul (räsitabeli sisu krüpteerimisel kasutatav sümmeetriline võti). </p><p>Vaikimisi kasutatava `AES` algoritmi puhul peab võti olema alati 128 bitti</p><p>Näide 128 bitise võtme genereerimisest openssl'ga `openssl rand -base64 16` </p>|
| `eeidas.client.hazelcast-encryption-alg`  | Ei | Krüpteerimisalgoritm vastavalt standardsele [Java Krüptograafiliste Algoritmide nimistule](https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#Cipher). Vaikimisi `AES`. |

<sup>1</sup> Kohustuslik juhul kui `eidas.client.hazelcast-enabled` on määratud.


Näide konfiguratsioonist:
```
eidas.client.hazelcast-enabled = true
eidas.client.hazelcast-config = file:/etc/eidas-client/hazelcast.xml
eidas.client.hazelcast-signing-key=JgeUmXWHRs1FClKuStKRNWvfNWfFHWGSR8jgN8_xEoBSGnkiHHgEEHMttYmMtzy88rnlO6yfmQpSAJ0yNA9NWw
eidas.client.hazelcast-signing-algorithm=HS512
eidas.client.hazelcast-encryption-key=K7KVMOrgRj7Pw5GDHdXjKQ==
eidas.client.hazelcast-encryption-alg=AES
```

Tabel 2.3.7 - Hazelcast kasutusstatistika otspunkt

| Parameeter        | Kohustuslik | Kirjeldus, näide |
| :---------------- | :---------- | :----------------|
| `management.endpoint.hazelcast.enabled`  | Ei | Võimalikud väärtused: `true`, `false`. Lülitab sisse `/hazelcast` otspunkti. Vaikimisi `false`. |


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

<a name="heartbeat"></a>
## 5. Monitoorimine - rakenduse oleku pärimine

Rakenduse oleku info on kättesaadav otspunktilt **/heartbeat**.

Rakenduse oleku info kuvamiseks kasutatakse Spring Boot Actuator raamistikku. Vaikeseadistuses on kõik otspunktid, välja arvatud **/heartbeat** otspunkt, välja lülitatud.

Lisaotspunkte on võimalik vajadusel seadistada vastavalt juhendile: <https://docs.spring.io/spring-boot/docs/2.2.1.RELEASE/reference/htmlsingle/#production-ready-endpoints-enabling-endpoints> (NB! rakendust war failina eraldiseisvasse Tomcat rakendusserverisse paigaldades on otspunktide seadistus piiratud lisa otspunktide sisse- ja väljalülitamisega).



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

Hazelcasti monitooringuks on võimalik kasutada Hazelcasti enda [health otspunkti](https://docs.hazelcast.org/docs/3.12/manual/html-single/index.html#health-check) (vaikimisi väljalülitatud).

Lisaks on võimalik detailsemat infot saada [diagnostika logist](https://docs.hazelcast.org/docs/3.12/manual/html-single/index.html#diagnostics) ja kontrollida klastrit detailsemalt, kui sisse lülitada [JMX pordi kasutus](https://docs.hazelcast.org/docs/3.11/manual/html-single/index.html#monitoring-with-jmx).

eIDAS-Client'i pakub ka `/hazelcast` otspunkti (vaikimisi välja lülitatud) minimaalse kasutusstatistikaga.


