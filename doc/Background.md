## Mõisted

eIDAS konnektorteenus - 
SAML - 


## Asutuse metateabe avaldamine

eIDAS konnektorteenus vajab SAML autentimispäringu vastuvõtuks ja vastuse koostamiseks asutuse metateavet. eIDAS klient avaldab oma metateabe `/metadata` otspunktis. Metateave genereeritakse ja allkirjastatakse iga pöördumise korral.

## eIDAS konnektorteenuse metateabe laadimine

eIDAS konnektorteenuse metateave loetakse sisse eIDAS kliendi käivitamisel (eenevalt seadistatud URLilt) ja puhverdatakse. Puhvrit värskendatakse jooksvalt vastavalt [SAML metadata](https://docs.oasis-open.org/security/saml/v2.0/saml-metadata-2.0-os.pdf) parameetrite `validUntil` või `cacheDuration` kasutusele.

Ilma konnektorteenusele ligipääsu omamata eIDAS klient ei käivitu.

## Isikutuvastusprotsess

Lihtsustatult toimib isikutuvastusprotsess eIDAS kliendi ja eIDAS konnektorteenuse vahel järgmiselt (vt joonis 1.)

1. Kasutaja navigeerib avalehele `/login`, mille peale kuvab eIDAS klient sihtriikide valiku vormi.

2. Kasutaja valib sihtriigi ja soovi korral autentimistaseme ning kas ta soovib tulemusi masinloetaval või inimloetaval kujul. Kasutaja vajutab 'Login'. Veebileht teeb HTTP POST päringu `/login` lehele koos valitud parameetritega. Eidas kliendi serveri poolel pannakse kokku `SAMLRequest` parameetri sisu ja tagastatakse kasutajale ümbersuunamisvorm, mis suunatakse automaatselt RIA eIDAS konnektorteenusesse.

3. Sirvik suunab kasutaja automaatselt eIDAS konnektorteenusesse koos `SAMLRequest`, `RelayState` ja `Country` parameetritega, kus teostatakse järgnevate sammudena ära kogu ülepiirilise isikutuvastuse sammud. Sealhulgas suunatakse kasutaja ümber sihtriigi eIDAS Node teenusesse, vajadusel küsitakse kasutaja nõusolekut andmete avaldamiseks ning teostatakse isikutuvastus.

4. Peale ülepiirilise isikutuvastusprotsessi läbimist suunab eIDAS konnektorteenus tulemuse eIDAS kliendi `/returnUrl` aadressile, koos `SAMLResponse` ja `RelayState` parameetriga. eIDAS klient valideerib vastuse, dekrüpteerib sisu ning kuvab tuvastatud isiku andmed.

<img src='img/EidasClient-Isikutuvastus.png'>
Joonis 1.


## Toetatud riikide nimekiri

NB! Toetatud sihtriikide nimekiri (JSON vormingus) laetakse konfiguratsioonis määratud URL-lt rakenduse käivitamise ajal ning puhverdatakse (puhvri aegumine on seadistatav). JSON vorming vt [Toetatud riikide nimekiri](https://github.com/e-gov/eIDAS-Connector/blob/master/Spetsifikatsioon.md#toetatud-riikide-nimekiri).