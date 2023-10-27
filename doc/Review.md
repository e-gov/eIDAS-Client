
## eIDAS-Client arhitektuuriline tagasivaade

04.05.2018

### Mis on eIDAS-Client?

eIDAS-Client on mikroteenus, mis tegutseb tõlgina autentimisteenuse TARA ja piiriülese autentimistaristu eIDAS vahel.

Tõlgiks olemine tähendab - mõnevõrra lihtsustatult - kahte asja:
1) lihtsa HTTP GET päringuna väljendatud autentimissoovi teisendamist eIDAS SAML protokollile vastavaks sõnumiks;
2) vastupidist teisendust - SAML autentimisvastuse tõlkimist TARAlihtsasse JSON-vormingusse.

eIDAS-Client peidab eIDAS SAML keerukuse. (SAML on tõeliselt keerukas XML-põhine turvalise andmevahetuse standard.) Muuhulgas hoolitseb eIDAS-Client sõnumite allkirjastamise ja dekrüpteerimise ning eIDAS Node-i ja TARA vahelise metateabe vahetuse ja kontrollimise eest.

eIDAS-Client oli mõeldud teostada kolmes kehastuses: 1) teek; 2) mikroteenus; 3) mikroteenusega seotud UI. Nüüd, kui eIDAS-Client on peaaegu valmis, on sõelale  jäänud mikroteenus. Kui vajadus peaks tekkima, on eIDAS-Client siiski kasutatav ka teegina. Skoobi kitsenemise põhjustasid ennustamatud, meie mõjuala välised muutused eIDAS ökosüsteemis. Skoobi kitsenemises pole midagi halba, pigem vastupidi.

### Kuidas keerukuse kapseldamine õnnestus?

Võib lugeda õnnestunuks. Mikroteenus teenindab TARA serverit, suheldes lihtsa ja selge API kaudu. SAML on TARA eest täielikult varjestatud. Tõenduseks eIDAS-Client-i kasutamise näide.

Välismaalase autentimiseks saadab TARA eIDAS-Client-i päringu:


```
GET https://eidas-client.example.org/login?

country=SE&LoA=high&RelayState=kse2vna8221lyauej'
```

Päringu tähendus on "palun autentida rootslane (`SE`), kasutades kõrge tagatistasemega (`LoA=high`) autentimismeetodeid. `RelayState` on tehniline parameeter päringu ja vastuse seostamiseks.

eIDAS-Client teisendab päringu SAML-protokolli, allkirjastab, kontrollib eIDAS-Node metaandmeid jms. Desifreerib vastussõnumi, kontrollib allkirja ja metaandmeid ning edastab TARA-sse JSON-struktuuri:

```
{
   "levelOfAssurance":"http://eidas.europa.eu/LoA/substantial",
   "attributes":{
      "DateOfBirth":"1965-01-01",
      "PersonIdentifier":"12345",
      "FamilyName":"Sven",
      "FirstName":"Svensson"
   }
}
```

Vastus on ise ennast selgitav.

### Kui suur on mikroteenuse koodimaht?

|     | LOC        |
|------|-----------|
| kood | 1900 |
| ühiktestide kood | 1300 |
| integratsioonitestid | 3200 |
| KOKKU | 6400 |

### Milline oli arenduse töömaht?

| arendusaeg | 4 kuud | 
|------|-----------|
| arendustunde | 600  |

Arvude tõlgendamisel tuleb arvestada, et kuigi eesmärk oli suhteliselt selge, sisaldas töö uue, keerulise tehnoloogia ja seda teostava teegi (OpenSAML) tundmaõppimist ning rakendamist.
