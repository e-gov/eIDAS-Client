<img src='doc/img/ee_cef_0.png' style="width:400px"></img>

# eIDAS-Client

## Mis on eIDAS-klient?

eIDAS-klient projekt on näidislahendus, mis vastavalt [eIDAS konnektorteenuse liidese spetsifikatsioonis](https://github.com/e-gov/eIDAS-Connector/blob/master/Spetsifikatsioon.md) sätestatud nõuetele [suhtleb](doc/Background.md) Eesti eIDAS Node konnektorteenusega.


Lahendus koosneb kahest osast:

1. **Teek** - pakub usaldustoiminguteks vajalikke funktsioone, sh teenusepakkuja metateabe koostamine, konnektorteenuse metateabe hankimine, autentimispäringute koostamine ja -vastuse valideerimine ning töötlus.
2. **Veebiteenus** - mikroteenus, mis pakub veebiliidest SAML spetsiifiliste operatsioonide teostamiseks. Toetatud on teenusepakkuja metateabe publitseerimine, konnektorteenuse metateabe töötlus ja autentimispäringute saatmine ning vastuvõtt konnektorteenuselt.

### Arhitektuur

Ülevaate eIDAS-klient projekti ehitusest ja komponentidest leiab [**siit**](doc/Structure.md).


###  Veebiteenuse API

Ülevaate sellest, milliseid otspunkte pakub eIDAS-klient veebiteenus, leiad [**siit**](doc/Service-API.md).



### Veebiteenuse ehitamine ja paigaldamine

eIDAS-klient veebiteenuse paigaldamiseks ja käivitamiseks vajalikud tegevused lühidalt (eeldab Java 1.8+):

1. Hangi githubist viimane lähtekood
`git clone https://github.com/e-gov/eIDAS-Client.git`

2. Ehita eIDAS-klient projekt
`./mvnw clean install`

3. Genereeri näidisvõtmed ja nende viitav konfiguratsioonifail (või loo ise, vt [**Seadistamine**](/doc/Configuration.md))
`eidas-client-webapp/src/test/resources/scripts/generateTestConfiguration.sh`

4. Käivita veebiteenus
`java -Dspring.config.location="./eidas-client-webapp/target/generated-test-conf/application.properties" -jar eidas-client-webapp/target/eidas-client-webapp-1.0-SNAPSHOT.war`

5. Veendumaks, et rakendus käivitus edukalt, ava brauseris URL http://localhost:8889/metadata

> NB! Selleks, et eidas-klienti reaalselt test konnektorteenuse vastu kasutada tuleb sõlmida liitumisleping ning edastada RIA-le genereeritud võtmehoidlast metateabe avalik võti (genereeritud näidiskonfiguratsiooni puhul /eidas-client-webapp/target/generated-test-conf/sp_metadata.crt)

Pikema ja täpsema seletuse selle kohta, kuidas veebiteenust paigaldada ja seadistada leiad [**integraatori juhendist**](doc/Configuration.md).