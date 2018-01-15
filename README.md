# eIDAS-Client

1) Java rakendus, mis
2) ühendub RIA eIDAS konnektorteenuse külge
3) pakub kasutajale UI-d, kus kasutaja valib riigi, kust ta tuleb ja vajutab "Autendi mind"
4) ja rakendus suunab konnektorteenusesse ja tagasi tulles teatab kasutajale "Welcome to Estonia, Javier Garcia!",
5) seejuures logides nii SAML-sõnumi saatmise kui ka vastuvõtmise
5) juhul kui autentimise teeb CEF Validation Service
6) või kasutaja reaalse nime, kui autentimise teeb mõne reaalse riigi eIDAS autentimisteenus (sõltub RIA konnektorteenuse seadistusest)
7) ja mis on piisavalt lihtne ja dokumenteeritud, et seda saab kergesti lõimida Eesti e-teenustesse, sh TARA-sse
  - spetsifikatsioon võiks asuda siin: https://github.com/e-gov/eIDAS-Connector/blob/master/Spetsifikatsioon.md
  - ja koosneda täpsest seletusest, mis vorminguga sõnumeid tuleb saata, mis tuleb vastuseks, mida tähendavad veakoodid jms
8) ja Nortalil on võimekus vajadusel nõustada liidestujaid.

eIDAS-Client ühe visioonina võiks koosneda kahest osast: 1) Java teek (package), nimega nt `ee.ria.eidas`. Teegis oleksid klassid jm artefaktid, mida eeldatavalt liidestuja saab ja soovib otseselt kasutada; 2) ümbrise v kesta pakett, mis teostaks seda, mida liidestuja tõenäoliselt otse üle ei võta (nt lipukeste kuvamine).
