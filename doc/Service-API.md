# Liidese spetsifikatsioon
-----------

## **Otspunktid**

Kõik päringud ja vastused esitatakse UTF-8 kodeeringus.
HTTPS kasutamine on kohustuslik. Soovituslik on kasutada kahesuunalist HTTPS-i.

`X-Request-ID` ja `X-Correlation-ID` päiste kasutamine on soovituslik. Päiste kasutamisel logitakse vastavate päiste väärtused logikirjetes `requestId` ja `sessionId` väärtustena, mis lihtsustab oluliselt logisündmuste seostamist erinevate süsteemi osade vahel.

Meetod | HTTP päring | Kirjeldus
------------- | ------------- | -------------
[**login**](Service-API.md#login) | **GET** /login | Moodustab ja tagastab ülepiirilise isikutuvastusprotsessi algatamise jaoks vajaliku [päringu](https://e-gov.github.io/eIDAS-Connector/Spetsifikatsioon#6-autentimisp%C3%A4ring) koos HTML ümbersuunamisvormiga.
[**returnUrl**](Service-API.md#returnUrl) | **POST** /returnUrl | Ülepiirilise isikutuvastuse tulemuse kontroll. SAML vastuse valideerimine vastavalt [SAML 2 Web SSO profiilile](https://docs.oasis-open.org/security/saml/v2.0/saml-profiles-2.0-os.pdf) ja [konnektorteenuse spetsifikatsioonile](https://e-gov.github.io/eIDAS-Connector/Spetsifikatsioon#7-autentimisvastus). Kontrollide edukal läbimisel isikuandmete tagastamine.
[**metadata**](Service-API.md#metadata) | **GET** /metadata | Tagastab eIDAS klient teenuse [SAML metaandmed](https://e-gov.github.io/eIDAS-Connector/Spetsifikatsioon#53-teenusepakkuja-metateave).
[**heartbeat**](Service-API.md#heartbeat) | **GET** /heartbeat või /heartbeat.json | Tagastab infot eIDAS klient teenuse versiooni ja oleku kohta.
[**hazelcast**](Service-API.md#hazelcast) | **GET** /hazelcast või /hazelcast.json | Tagastab infot eIDAS klient teenuses jooksva Hazelcast klastri eksemplari kohta. **Vaikimisi välja lülitatud**.


<a name="login"></a>
## **login**


### Päring

Parameetrid:

| Parameetri nimi        | Kohustuslik           | Selgitus  |
| ------------- |:-------------:| :-----|
| **Country** |	Jah | Parameeter määrab ära tuvastatava kodaniku riigi ([ISO 3166-1 alpha-2](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2) kood). |
| **LoA** |	Ei | Parameeter, määrab nõutava eIDAS isikutuvastuse taseme. Üks järgnevatest väärtustest: `LOW`, `SUBSTANTIAL`, `HIGH`. Kui parameeter on määramata, siis vaikimisi loetakse väärtuseks `SUBSTANTIAL`. |
| **RelayState** |	Ei | Parameeter, mis saadetakse edasi konnektorteenusele muutmata kujul. Väärtus peab vastama regulaaravaldisele `[a-zA-Z0-9-_]{0,80}`. |
| **Attributes** | Ei | Parameeter, sisaldab tühikuga eraldatud nimekirja eIDAS atribuutidest (nn *FriendlyName* kujul), mida autentispäringus sihtriigi eIDAS identiteediteenuselt küsitakse. Tühikud esitada kasutades URL kodeeringut ([RFC 3986](https://www.ietf.org/rfc/rfc3986.txt)). Lubatud eIDAS atribuudid: `FamilyName`, `FirstName`, `DateOfBirth`, `PersonIdentifier`, `BirthName`, `PlaceOfBirth`,`CurrentAddress`,`Gender`, `LegalPersonIdentifier`, `LegalName`, `LegalAddress`, `VATRegistration`, `TaxReference`, `LEI`, `EORI`, `SEED`, `SIC`, `D-2012-17-EUIdentifier` (vt ka atribuutide kirjeldusi [eIDAS Atribuutide profiilis](https://ec.europa.eu/cefdigital/wiki/download/attachments/46992719/eIDAS%20SAML%20Attribute%20Profile%20v1.1_2.pdf?version=1&modificationDate=1497252920100&api=v2)). Kui parameeter on määramata, siis vaikimisi loetakse väärtuseks nimikiri järgnevatest parameetritest: `FamilyName`, `FirstName`, `DateOfBirth`, `PersonIdentifier`|

Näide:
```bash
curl 'https://localhost:8889/login?Country=CA'
```

```bash
curl 'https://localhost:8889/login?Country=CA&LoA=LOW'
```

```bash
curl 'https://localhost:8889/login?Country=CA&LoA=LOW&RelayState=kse2vna8221lyauej'
```

```bash
curl 'https://localhost:8889/login?Country=CA&LoA=LOW&RelayState=kse2vna8221lyauej&Attributes=LegalPersonIdentifier%20LegalName%20LegalAddress'
```


### Vastus

**Eduka vastuse** korral tagastatakse HTTP staatuskood 200 koos sihtriiki suunamiseks vajaliku SAML päringu ja HTML ümbersuunamisvormiga.

| Atribuudi nimi        | Kohustuslik           | Selgitus  |
| ------------- |:-------------:| :-----|
| **country** |	Jah | Parameeter määrab ära tuvastatava kodaniku riigi. Väärtus peab vastama [ISO 3166-1 alpha-2](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2) standardis toodule. |
| **SAMLRequest** |	Jah | [Konnektorteenuse spetsifikatsioonile](https://e-gov.github.io/eIDAS-Connector/Spetsifikatsioon#6-autentimisp%C3%A4ring) vastav SAML `AuthnRequest` päring.  |
| **RelayState** |	Ei | Parameeter, mis saadetakse edasi konnektorteenusele muutmata kujul. Väärtus peab vastama regulaaravaldisele `[a-zA-Z0-9-_]{0,80}`. |

Näide:
```xml
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
    <body onload="document.forms[0].submit()">
        <noscript>
            <p>
                <strong>Note:</strong> Since your browser does not support JavaScript,
                you must press the Continue button once to proceed.
            </p>
        </noscript>
        <form action="https&#x3a;&#x2f;&#x2f;eidastest.eesti.ee/&#x3a;8080&#x2f;EidasNode&#x2f;ServiceProvider" method="post">
            <div>
                <input type="hidden" name="SAMLRequest" value="PD94bWw...........MnA6QXV0aG5SZXF1ZXN0Pg=="/>
                <input type="hidden" name="country" value="CA"/>
            </div>
            <noscript>
                <div>
                    <input type="submit" value="Continue"/>
                </div>
            </noscript>
        </form>
    </body>
</html>
```

**Vea korral** moodustatakse vastus vastavalt [**veakäsitlus**](Service-API.md#veakasitlus) peatükis toodule. Võimalikud veaolukorrad on toodud järgnevas tabelis:

| HTTP staatuskood  | Vea lühikirjeldus | Viga selgitav tekst  |
| :-------------: |:-------------| :-----|
| 400 | Bad request | Required String parameter 'country' is not present |
| 400 | Bad request  | Invalid country! Valid countries:[...] |
| 400 | Bad request  | Invalid LoA! One of [...] expected. |
| 400 | Bad request  | Invalid RelayState! Must match the following regexp: [...] |
| 400 | Bad request  | Found one or more invalid Attributes value(s). Valid values are: [...] |
| 400 | Bad request  | Attributes value '[.]' is not allowed. Allowed values are: : [...] |
| 403 | Forbidden | Endpoint not allowed to be accessed via port number [...] |
| 405 | Method Not Allowed | Request method [...] not supported |
| 500 | Internal Server Error | Something went wrong internally. Please consult server logs for further details. |



------------------------------------------------


<a name="returnUrl"></a>
## **returnUrl**

### Päring

| Päised |
| :-------------------------- |
| `Content-Type: application/x-www-form-urlencoded` |

| Parameeter      | Kohustuslik           | Selgitus  |
| ------------- |:-------------:| :-----|
| **SAMLResponse** | Jah | Ülepiirilisest autentimiskanalist tulev SAML vastus (Base64 kodeeritud). |

Näide:

```bash
curl -X POST \
  https://localhost:8889/returnUrl \
  -H 'content-type: application/x-www-form-urlencoded' \
  -d 'SAMLResponse=..........................'
```

### Vastus

**Eduka autentimise** korral tagastatakse **HTTP 200** koos isikuandmetega (vt Tabel 1).

Atribuudi nimi | Kohustuslik | Selgitus | Tüüp
------------ | ------------- | ------------- | -------------
**levelOfAssurance** | Jah  | eIDAS autentimistase. Võimalikud väärtused: `http://eidas.europa.eu/LoA/low`, `http://eidas.europa.eu/LoA/substantial`, `http://eidas.europa.eu/LoA/high` | **String**
**attributes** | Jah | Sisaldab atribuute autenditud isiku andmetega. Atribuudid esitatakse võti-väärtus paaridena, kus võti on `FriendlyName` ja väärtus `AttributeValue` elemendi sisu vastavalt eIDAS SAML Attribute Profile dokumendile (vt [Viited](https://e-gov.github.io/eIDAS-Connector/Viited)). <p>**Kohustuslikud atribuudid** - sisaldavad andmeid, mida liikmesriigid on kohustatud tagastama.</p><p> 1. Füüsilise isiku kohta tagastatakse alati vaikimisi neli atribuuti: `FirstName`, `FamilyName`, `PersonIdentifier` ja `DateOfBirth`.</p><p>2. Juriidilise isiku kohta tagastatakse alati `LegalPersonIdentifier`, `LegalName` väärtused **ainult juhul** kui päringus selleks soovi avaldatakse.</p><p>**Mittekohustulikud lisaatribuudid** - Lisaks on võimalik küsida eIDAS lisaatribuute, mis tagastatakse ainult juhul kui sihtriik neid toetab ja päringus selleks soovi avaldatakse:<ul><li>Füüsilise isiku kohta: `BirthName`, `PlaceOfBirth`, `CurrentAddress`, `Gender`</li><li>Juriidilise isiku kohta: `LegalAddress`, `VATRegistration`, `TaxReference`, `LEI`, `EORI`, `SEED`, `SIC`, `D-2012-17-EUIdentifier`</li></ul><p>**Isiku esindaja andmed** - Täiendavalt on võimalik, et sihtriik saadab lisaandmeid isiku esindaja kohta (küsida ei saa): `RepresentativeBirthName`, `RepresentativeCurrentAddress`, `RepresentativeFamilyName`, `RepresentativeFirstName`, `RepresentativeDateOfBirth`, `RepresentativeGender`, `RepresentativePersonIdentifier`, `RepresentativePlaceOfBirth`, `RepresentativeD-2012-17-EUIdentifier`, `RepresentativeEORI`, `RepresentativeLEI`,`RepresentativeLegalAddress`, `RepresentativeLegalName`, `RepresentativeLegalAddress`, `RepresentativeLegalPersonIdentifier`, `RepresentativeSEED`, `RepresentativeSIC`,`RepresentativeTaxReference`, `RepresentativeVATRegistration`</p> | **Objekt**
**attributes.FirstName** | Jah | Isiku eesnimi. | **String**
**attributes.FamilyName** | Jah | Isiku perenimi. | **String**
**attributes.PersonIdentifier** | Jah | Isikut identifitseeriv unikaalne kood. <br><br>Esitatakse formaadis XX+ “/“ + YY + “/“ + ZZZZZZ..., kus XX on identifitseeritud isiku riigi kood (ISO 3166-1 alpha-2), YY on riigi kood (ISO 3166-1 alpha-2) mille isikut soovitakse autentida ning ZZZZZZ... on sihtriigi isikut identifitseeriv kood (täpne formaat sõltub sihtriigist). | **String**
**attributes.DateOfBirth** | Jah | Sünniaeg formaadis: YYYY + “-“ + MM + “-“ + DD (kus YYYY on aasta, MM on kuu ning DD päev) | **String**
**attributes.LegalPersonIdentifier** | Ei | Juriidilise isiku kood. Tagastatakse ainult juhul kui kasutaja selleks soovi avaldab. | **String**
**attributes.LegalName** | Ei | Juriidilise isiku nimi. Tagastatakse ainult juhul kui kasutaja selleks soovi avaldab. | **String**
**attributesTransliterated** | Ei | Sisaldab atribuutide väärtuseid translitereeritud kujul. Atribuudid esitatakse võti-väärtus paaridena, kus võti on `FriendlyName` ja väärtus `AttributeValue` elemendi translitereeritud sisu vastavalt eIDAS SAML Attribute Profile dokumendile (vt [Viited](https://e-gov.github.io/eIDAS-Connector/Viited)). |  **Objekt**
Tabel 1.

Näide:
```json
{
   "levelOfAssurance":"http://eidas.europa.eu/LoA/substantial",
   "attributes":{
      "DateOfBirth":"1965-01-01",
      "PersonIdentifier":"CA/CA/12345",
      "FamilyName":"Ωνάσης",
      "FirstName":"Αλέξανδρος"
   },
   "attributesTransliterated":{
      "FamilyName":"Onassis",
      "FirstName":"Alexander"
   }
}
```

**Ebaeduka autentimise** korral tagastatakse **HTTP 401** ning [**veakirjeldus**](Service-API.md#veakasitlus) vastavalt peatükis toodule. Võimalikud autentimise ebaõnnestumise olukorrad on toodud järgnevas tabelis:

| HTTP staatuskood  | Vea lühikirjeldus | Viga selgitav tekst  |
| :-------------: |:-------------| :-----|
| 401 | Unauthorized | Authentication failed |
| 401 | Unauthorized | No user consent received. User denied access. |

**Muude vigade** korral moodustatakse vastus vastavalt [**veakäsitlus**](Service-API.md#veakasitlus) peatükis toodule. Võimalikud veaolukorrad on toodud järgnevas tabelis:

| HTTP staatuskood  | Vea lühikirjeldus | Viga selgitav tekst  |
| :-------------: |:-------------| :-----|
| 400 | Bad request | Required String parameter 'SAMLResponse' is not present |
| 400 | Bad request  | Invalid SAMLResponse. [...] |
| 403 | Forbidden | Endpoint not allowed to be accessed via port number [...] |
| 405 | Method Not Allowed | Request method [...] not supported |
| 500 | Internal Server Error | Something went wrong internally. Please consult server logs for further details. |

------------------------------------------------


<a name="metadata"></a>
## **metadata**


### Päring

Parameetrid puuduvad.

Näide:
```bash
curl 'https://localhost:8889/metadata'
```

### Vastus

**Eduka vastuse** korral tagastatakse HTTP staatuskood 200 ning XML metadata.

Näide:
```xml
<md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" ID="_dst76fjthbqaxisvsrros6nytpf9m4sz8daw0ch" entityID="https://localhost:8081/metadata" validUntil="2018-03-13T13:40:21.927Z">
	<ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
		<ds:SignedInfo>
			<ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
			<ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512"/>
			<ds:Reference URI="#_dst76fjthbqaxisvsrros6nytpf9m4sz8daw0ch">
				<ds:Transforms>
					<ds:Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"/>
					<ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
				</ds:Transforms>
				<ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha512"/>
				<ds:DigestValue>
aX3WTeCMC37Y/qutWVGmwSGFzjjx7+dpoYfvg7RGlkmfGJTSzohUpsZXoHB9W6nKcZoL5MhcscfG Ku4F2ZovIw==
				</ds:DigestValue>
			</ds:Reference>
		</ds:SignedInfo>
		<ds:SignatureValue>
L+5MkF5MyiYZAUl6mCOBdl+d87mLp0m1AaTS/9SLP72K4XZh00iFKh5FMyC+iUiP2nZAgKFWVeNE myR+rl+JejTm3EzdrVbKhRVSEcl+dTpBEZ6APLQZMwe/8KmaRR7L
		</ds:SignatureValue>
		<ds:KeyInfo>
			<ds:X509Data>
				<ds:X509Certificate>
MIIB4jCCAWagAwIBAgIEW1u+vzAMBggqhkjOPQQDAgUAMEcxCzAJBgNVBAYTAkVFMQ0wCwYDVQQK EwR0ZXN0MQ0wCwYDVQQLEwR0ZXN0MRowGAYDVQQDExFTUC1tZXRhZGEtc2lnbmluZzAeFw0xODAz MDkxNjE1NTRaFw0yMDAzMDgxNjE1NTRaMEcxCzAJBgNVBAYTAkVFMQ0wCwYDVQQKEwR0ZXN0MQ0w CwYDVQQLEwR0ZXN0MRowGAYDVQQDExFTUC1tZXRhZGEtc2lnbmluZzB2MBAGByqGSM49AgEGBSuB BAAiA2IABGj1C5gvuR8ZG7Q5b5KSYFV3QzDwo+2aewjBm+SKIotc+5HBUGelflKJn7fKJQfVGwEc I+oVvXcIs0XyV4qQIHT3ylh4SlZg9AUUSZeF2ktLTEHApJ8wHpt89WF+oKqFu6MhMB8wHQYDVR0O BBYEFPd/0ir9wkxXsq1gHdz6CkcSOfQMMAwGCCqGSM49BAMCBQADaAAwZQIxAKab7Kc2NMLyFyMr tGWbHKKq28b5yJoy2//vqjZrVFuRUflYfQnom5Na9za3VYptUQIwPZF083qWwyJNAIK0Qc1c2Lir d0CVMSovoZUCvLmNNWwBUjqTdqIY/3PDO6PRGloT
				</ds:X509Certificate>
			</ds:X509Data>
		</ds:KeyInfo>
	</ds:Signature>
	<md:Extensions xmlns:alg="urn:oasis:names:tc:SAML:metadata:algsupport">
		<eidas:SPType xmlns:eidas="http://eidas.europa.eu/saml-extensions">public</eidas:SPType>
		<alg:SigningMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512"/>
	</md:Extensions>
	<md:SPSSODescriptor AuthnRequestsSigned="true" WantAssertionsSigned="true" protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
		<md:KeyDescriptor use="signing">
			<ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
				<ds:X509Data>
					<ds:X509Certificate>
MIIB7zCCAXKgAwIBAgIEFWvpjzAMBggqhkjOPQQDAgUAME0xCzAJBgNVBAYTAkVFMQ0wCwYDVQQK EwR0ZXN0MQ0wCwYDVQQLEwR0ZXN0MSAwHgYDVQQDExdTUC1hdXRoLXJlcXVlc3Qtc2lnbmluZzAe Fw0xODAzMDkxNjE1NTVaFw0yMDAzMDgxNjE1NTVaME0xCzAJBgNVBAYTAkVFMQ0wCwYDVQQKEwR0 ZXN0MQ0wCwYDVQQLEwR0ZXN0MSAwHgYDVQQDExdTUC1hdXRoLXJlcXVlc3Qtc2lnbmluZzB2MBAG ByqGSM49AgEGBSuBBAAiA2IABNqM3bEf8xJl3dvpeqM5rF+pJxAw9ao3hFK2D40j8FMmtkTxUt4b f/WQrg0DhW+Qudkdd8nGpzKieF7hIQ1I9WVWW71alaxwcVggR2iD0SpMcnbvjfQ1/zRu16Yw6TjS IaMhMB8wHQYDVR0OBBYEFMeaE0rtTLhOrnBjb/2sDPuuEw+dMAwGCCqGSM49BAMCBQADaQAwZgIx AIW7dSy696VgJkRWYMC3tpqViQGGSXF10qbpXycCSbf5HTvG02OfO/y/lSUduUwsywIxAJEEQZAp JSyRx3O3cmsKqPS/I4lY6pmOfdBCoJK8RRIqHIIIlfvEvoX7koO4wLbgwg==
					</ds:X509Certificate>
				</ds:X509Data>
			</ds:KeyInfo>
		</md:KeyDescriptor>
		<md:KeyDescriptor use="encryption">
			<ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
				<ds:X509Data>
					<ds:X509Certificate>
MIIFNzCCAx+gAwIBAgIEfHFvpTANBgkqhkiG9w0BAQsFADBMMQswCQYDVQQGEwJFRTENMAsGA1UE ChMEdGVzdDENMAsGA1UECxMEdGVzdDEfMB0GA1UEAxMWU1AtcmVzcG9uc2UtZW5jcnlwdGlvbjAe Fw0xODAzMDkxNjE1NTdaFw0yMDAzMDgxNjE1NTdaMEwxCzAJBgNVBAYTAkVFMQ0wCwYDVQQKEwR0 ZXN0MQ0wCwYDVQQLEwR0ZXN0MR8wHQYDVQQDExZTUC1yZXNwb25zZS1lbmNyeXB0aW9uMIICIjAN BgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAiWMUi8QBhP9w5rt32ICTxwDPorbfcqioP4UDmGQf iZjf4+/bzYMO0l6qwJHb1//McQ2KKEgcVGGZgJia9yFjjPSjJlmAKP26aPjTXmmshNGsZG7ErDK4 +Y9B2TXZnDIDbKPLliT4KlCTUbC9YSeWC1/6Z05fn1ggWORBoSmi1vndzfZ7yPHxA0TvvFC6vEGx cnuOh8diF5iYzaWV3MTrxwSFJ2uBKkBOpDStPwZNRS/hEcFPEoRzRU5dPET+YkNkZcQmofzYI9zK t6XDx0dzCWLwBsSNeAwK5Yn84zYNPqFzGE2fCubL7X7eUVaVaXGqU49hJEVKPCNsigQwennuq/GC xt/HtIe9XI4Z+ScbFBvL2CVSUk+562f6jTOBjrJJbrjafWpk51xDFydGWyvYxpKJgmynT0sfyK5r TyK2g1CAkKwLgdxgBi/aoB21DZCdhvmntHjV+DFjaq5TEU9xQCAH2GkUdv8mbzmFUb+vvM7RtUVQ oskMxEM43Y+GoHPgcp2+lDJQ9rTV3INIFwE+XeP3HdnDpKrzeQqmPy1raIUJSpSQ6nG+K6bCbZrL I9wUCVgH6BJ1euD1mOjir4P6yP9+j7j6RCItM9weXPNEeG/ENZFZ9fBKJ+jNdqJW03zuOQWdYPlp YHtOKk46L9JruEF5jMbqXjxfmUuFCSlwPF8CAwEAAaMhMB8wHQYDVR0OBBYEFFJ47K8Dr0b/eIQI HsL6IPs5RJspMA0GCSqGSIb3DQEBCwUAA4ICAQBm1dmD7P3xJ3QBm9evVEAfPpGxp8b+elcceKHP NiWon73SH560cNXq9xgHeF9t4Ta35rptONSg/trxBew5y31MxaE/XRKT7CJcTa/1JKqapCgFS9NA L2O6+uiPJW+9xCEYD0x5xJ1Sq1njwCoGlfyFfh4NABbPmtDHrVHJzjaEHMw5YYHAREYPSLf0GHkS qCZ020qg3QJS0FYk+xOCKM63xDeGFSe+Qeo/bYhowbD65gdXjvNtMumfis7E4375dIUGrpdovm6D IPYb1h/PcoPC3gOaTaC3SnXx/FiSGWgnuRvJfifTCepsdIrojbWUh/2ffTBcTNOlXVC8Azxdud3s 7DaKun6XI3Q6DaQqlc13d4uuqbZG51uCb0GCTt36ATJ3vDs6G0NrKgskRaKmp5CJKAg75jOtq7UT Sg4ItvGvz9V8eMwZBJdqc6KaHcjlq6NCX5NFOHwBKvCsEi6e575w+UsUKliB6FepZ3VdIlC6Iq+X CYs/CwXLb8nZa6k3ZLoW6/K8eukv+5nYGyI3Ubf7Wi2E624hckG2DVBRPXHaWpODgYr5hIQt1FHE wrbTPHQn5yamuAWBhIEMeDgCMlYimW5DpCjm4ncstpTn+u2y6Oy9G6vzIRzI7OsneXEWUYSQAHei pZSiFLgSx7k5bj/6ocA0CxRzhCghhAvAbrqOfQ==
					</ds:X509Certificate>
				</ds:X509Data>
			</ds:KeyInfo>
		</md:KeyDescriptor>
		<md:NameIDFormat>
urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified
		</md:NameIDFormat>
		<md:AssertionConsumerService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="https://localhost:8081/returnUrl" index="0"/>
	</md:SPSSODescriptor>
</md:EntityDescriptor>
```

**Vea korral** moodustatakse vastus vastavalt [**veakäsitlus**](Service-API.md#veakasitlus) peatükis toodule. Võimalikud veaolukorrad on toodud järgnevas tabelis:

| HTTP staatuskood  | Vea lühikirjeldus | Viga selgitav tekst  |
| :-------------: |:-------------| :-----|
| 405 | Method Not Allowed | Request method [...] not supported |
| 500 | Internal Server Error | Something went wrong internally. Please consult server logs for further details. |

--------------------------------------------------


<a name="heartbeat"></a>
## **heartbeat**

Rakenduse töökorras olekut on võimalik pärida Spring Boot Actuator'i otspunkti **/heartbeat** või **/heartbeat.json** kaudu. 

### Päring

Parameetrid puuduvad.

Näide:
```bash
curl 'https://localhost:8889/heartbeat'
```

### Vastus

| Atribuudi nimi        | Kohustuslik           | Selgitus  |
| ------------- |:-------------:| :-----|
| **status** |	Jah | Parameeter, mis indikeerib rakenduse töökorras olekut. . Võimalikud väärtused: `UP`, `DOWN`  |
| **name** |	Jah | Rakenduse nimi.  |
| **version** |	Jah | Rakenduse versioon. |
| **buildTime** |	Jah | Rakenduse ehitamise aeg. Unix timestamp formaadis. |
| **startTime** |	Jah | Rakenduse käivitamise aeg. Unix timestamp formaadis. |
| **currentTime** |	Jah | Päringu sooritamise aeg. Unix timestamp formaadis. |
| **dependencies** |	Jah | Sisaldab nimekirja välistest süsteemidest, millest rakendus sõltub. Väliste süsteemide, millega on võimalik ühendust saada, `status` olekuna kuvatakse `UP`, mittevastavate süsteemide korral `DOWN`. Kui mõni väline süsteem, millest rakendus sõltub, on `DOWN`, siis on ka vastuse üldine `status` `DOWN`. |
| **dependencies.status** |	Jah | Välise süsteemi status. Võimalikud väärtused: `UP`, `DOWN`  |
| **dependencies.name** |	Jah | Välise süsteemi lühinimetus (näiteks: `eIDAS-Node`, `hazelcast`). |

Näide vastuse struktuurist:
```json
{
    "status": "UP",
    "name": "eidas-client-webapp",
    "version": "1.0.0-SNAPSHOT",
    "buildTime": 1528117155,
    "startTime": 1528121189,
    "currentTime": 1528121277,
    "dependencies": [
        {
            "status": "UP",
            "name": "eIDAS-Node"
        },
        {
            "status": "UP",
            "name": "hazelcast"
        }
    ]
}
```

**Töökorras rakenduse korral** tagastatakse HTTP staatuskood 200 ning JSON vastus, milles `$.status` väärtus on `UP`

Näide 1: edukas vastus
```bash
curl http://localhost:8889/heartbeat.json
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100   195    0   195    0     0    112      0 --:--:--  0:00:01 --:--:--   112{"status":"UP","name":"eidas-client-webapp","version":"1.0.0-SNAPSHOT","buildTime":1528829409,"startTime":1528877695,"currentTime":1528877733,"dependencies":[{"status":"UP","name":"eIDAS-Node"}]}
```

**Mittetöökorras rakenduse korral** (näiteks, kui tööks vajalik sõltuvus ei ole kättesaadav), tagastatakse HTTP staatuskood 200 ning JSON vastus, milles `$.status` väärtus on `DOWN`


Näide 2: tööks vajalik sõltuvus ei ole kättesaadav
```bash
$ curl http://localhost:8889/heartbeat.json
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100   199    0   199    0     0     98      0 --:--:--  0:00:02 --:--:--    98{"status":"DOWN","name":"eidas-client-webapp","version":"1.0.0-SNAPSHOT","buildTime":1528829409,"startTime":1528877695,"currentTime":1528877831,"dependencies":[{"status":"DOWN","name":"eIDAS-Node"}]}
```


<a name="hazelcast"></a>
## **hazelcast**

Juhul kui rakenduses on seadistatud Hazelcast, kuvatakse selle olek ja räsitabelite [metainfo](https://docs.hazelcast.org/docs/3.11/manual/html-single/index.html#map-statistics) Spring Boot Actuator'i otspunkti **/hazelcast** või **/hazelcast.json** kaudu.

NB! otspunkt on vaikimisi välja lülitatud.


### Päring

Parameetrid puuduvad.

Näide:
```bash
curl 'https://localhost:8889/hazelcast'
```

### Vastus

| Atribuudi nimi        | Kohustuslik           | Selgitus  |
| ------------- |:-------------:| :-----|
| **clusterState** |	Jah | Klastri olek. `ACTIVE` kui klaster on töökorras ja valmis päringuid teenindama. Võimalikud väärtused vastavalt [API dokumentatsioonile](https://docs.hazelcast.org/docs/3.11/manual/html-single/index.html#managing-cluster-and-member-states)  |
| **clusterSize** |	Jah | Klastriga liitunud liikmete arv.  |
| **maps** |	Jah | Massiiv klastris loodud räsitabelitest. |
| **maps[].mapName** |	Jah | Konkreetse räsitabeli nimi. |
| **maps[].creationTime** |	Jah | Räsitabeli loomise aeg. Unix timestamp formaadis. |
| **maps[].ownedEntryCount** |	Jah | Kirjete arv kohalikus eksemplaris. |
| **maps[].backupEntryCount** |	Jah | Varundamiskirjete arv kohalikus eksemplaris. |
| **maps[].backupCount** |	Jah |  Varukoopiate arv ühe kirje kohta. |
| **maps[].hitsCount** |	Jah | Kohaliku eksemplari lugemisoperatsioonide loendur. |
| **maps[].lastUpdateTime** |	Jah | Viimane kirje uuendamise aeg kohalikus eksemplaris. |
| **maps[].lastAccessTime** |	Jah | Viimane kirje lugemise aeg kohalikus eksemplaris. |
| **maps[].lockedEntryCount** |	Jah | Lukustatud kirjete arv kohalikus eksemplaris. |
| **maps[].dirtyEntryCount** |	Jah | Rakendumata uuendustega kirjete arv. |
| **maps[].totalGetLatency** |	Jah | GET operatsioonide maksimaalne latentsusaeg. |
| **maps[].totalPutLatency** |	Jah | PUT operatsioonide maksimaalne latentsusaeg. |
| **maps[].totalRemoveLatency** |	Jah | Kirjete kustutamise maksimaalne latsentsusaeg.  |
| **maps[].heapCost** |	Jah | Hoitud andmete maht baitides. |

Näide vastuse struktuurist:
```json
{
   "clusterState":"ACTIVE",
   "clusterSize":3,
   "maps":[
      {
         "mapName":"unansweredRequestsMap",
         "currentCapacity":0,
         "creationTime":1541962062911,
         "ownedEntryCount":0,
         "backupEntryCount":0,
         "backupCount":0,
         "hitsCount":0,
         "lastUpdateTime":0,
         "lastAccessTime":0,
         "lockedEntryCount":0,
         "dirtyEntryCount":0,
         "totalGetLatency":0,
         "totalPutLatency":0,
         "totalRemoveLatency: ":0,
         "heapCost":0
      }
   ]
}
```

**Sisselülitatud Hazelcasti** puhul tagastatakse HTTP staatuskood 200 ning JSON vastus:

Näide:
```bash
curl http://localhost:8889/hazelcast.json
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100   381    0   381    0     0   8106      0 --:--:-- --:--:-- --:--:--  8106{"clusterState":"ACTIVE","clusterSize":1,"maps":[{"mapName":"unansweredRequestsMap","currentCapacity":0,"creationTime":1541962062911,"ownedEntryCount":0,"backupEntryCount":0,"backupCount":0,"hitsCount":0,"lastUpdateTime":0,"lastAccessTime":0,"lockedEntryCount":0,"dirtyEntryCount":0,"totalGetLatency":0,"totalPutLatency":0,"totalRemoveLatency: ":0,"heapCost":0}]}
```


<a name="veakasitlus"></a>
## **Veakäsitlus**

### HTTP staatuskood

HTTP staatuskoode käsitletakse [RFC2616](https://tools.ietf.org/html/rfc2616) standardile vastavalt.


Näiteks tähistavad 400 vahemiku koodid kliendi päringu mittevastavust nõuetele (nagu puuduvad või lubamatu väärtusega parameetrid) ning staatuskoodid alates 500 serveripoolseid probleeme (nagu ülekoormus).

### HTTP vastuse keha

Veakirjeldus tagastatakse JSON objektina.

Atribuudi nimi | Kohustuslik | Selgitus | Tüüp
------------ | ------------- | ------------- | -------------
**error** | Jah  | Vea lühikirjeldus. | **String**
**message** | Jah  | Viga selgitav tekst. | **String**

Näide:
```json
{
   "error" : "Bad Request",
   "message" : "Required String parameter 'country' is not present"
}
```

