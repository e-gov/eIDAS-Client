# eIDAS-Client

1. Java ***rakendus***, mis
2. ühendub RIA eIDAS konnektorteenuse külge
3. pakub kasutajale UI-d, kus kasutaja valib riigi, kust ta tuleb ja vajutab "Autendi mind"
4. ja rakendus suunab konnektorteenusesse ja tagasi tulles teatab kasutajale "Welcome to Estonia, Javier Garcia!",
    1. juhul kui autentimise teeb CEF Validation Service
    2. või kasutaja reaalse nime, kui autentimise teeb mõne reaalse riigi eIDAS autentimisteenus (sõltub RIA konnektorteenuse seadistusest)
6. seejuures logides nii SAML-sõnumi saatmise kui ka vastuvõtmise
7. ja andes kasutajale mõistliku teate, kui midagi peaks untsu minema
8. ja mis on piisavalt lihtne ja dokumenteeritud, et seda saab kergesti lõimida Eesti e-teenustesse, sh TARA-sse
    1. järgib [eIDAS konnektorteenuse liidese spetsifikatsiooni](https://github.com/e-gov/eIDAS-Connector/blob/master/Spetsifikatsioon.md)
9. ja Nortalil on võimekus vajadusel nõustada liidestujaid.
10. omab ka teatud omadusi, millega saab automaatselt eIDAS-autentimist testida.

eIDAS-Client ühe visioonina võiks koosneda kahest osast: 1) Java ***teek*** (package), nimega nt `ee.ria.eidas`. Teegis oleksid klassid jm artefaktid, mida eeldatavalt liidestuja saab ja soovib otseselt kasutada; 2) ümbrise v kesta pakett, mis teostaks seda, mida liidestuja tõenäoliselt otse üle ei võta (nt lipukeste kuvamine).

eIDAS-Client-i ***tootena*** võiks välja pakkuda eIDAS adapteri nime all.  

~~Kui spetsifikatsioon, UI jm dokumentatsioon oleks inglise keeles, võiks teek potentsiaalselt huvi pakkuda teiste EL riikide konnektorite külge liidestujatele (võimalus promoda teeki CEF-is, ehk isegi saada tellimusi CEF-st või teistest riikidest).~~
