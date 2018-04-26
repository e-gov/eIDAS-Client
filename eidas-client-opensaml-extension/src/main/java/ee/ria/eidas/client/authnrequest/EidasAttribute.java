package ee.ria.eidas.client.authnrequest;

public enum EidasAttribute {
        PERSON_IDENTIFIER("PersonIdentifier", "http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier", true),
        CURRENT_FAMILY_NAME("FamilyName", "http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName", true),
        CURRENT_GIVEN_NAME("FirstName", "http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName", true),
        DATE_OF_BIRTH("DateOfBirth", "http://eidas.europa.eu/attributes/naturalperson/DateOfBirth", true),
        BIRTH_NAME("BirthName", "http://eidas.europa.eu/attributes/naturalperson/BirthName", false),
        PLACE_OF_BIRTH("PlaceOfBirth", "http://eidas.europa.eu/attributes/naturalperson/PlaceOfBirth", false),
        CURRENT_ADDRESS("CurrentAddress", "http://eidas.europa.eu/attributes/naturalperson/CurrentAddress", false),
        GENDER("Gender", "http://eidas.europa.eu/attributes/naturalperson/Gender", false),

        LEGAL_PERSON_IDENTIFIER("LegalPersonIdentifier", "http://eidas.europa.eu/attributes/legalperson/LegalPersonIdentifier", true),
        LEGAL_NAME("LegalName", "http://eidas.europa.eu/attributes/legalperson/LegalName", true),
        LEGAL_ADDRESS("LegalAddress", "http://eidas.europa.eu/attributes/legalperson/LegalPersonAddress", false),
        VAT_REGISTRATION("VATRegistration", "http://eidas.europa.eu/attributes/legalperson/VATRegistrationNumber", false),
        TAX_REFERENCE("TaxReference", "http://eidas.europa.eu/attributes/legalperson/TaxReference", false),
        LEI("LEI", "http://eidas.europa.eu/attributes/legalperson/LEI", false),
        EORI("EORI", "http://eidas.europa.eu/attributes/legalperson/EORI", false),
        SEED("SEED", "http://eidas.europa.eu/attributes/legalperson/SEED", false),
        SIC("SIC", "http://eidas.europa.eu/attributes/legalperson/SIC", false),
        D_2012_17_EUIdentifier("D-2012-17-EUIdentifier", "http://eidas.europa.eu/attributes/legalperson/D-2012-17-EUIdentifier", false);

        private String friendlyName;
        private String name;
        private boolean required;

        EidasAttribute(String friendlyName, String name, boolean required) {
            this.friendlyName = friendlyName;
            this.name = name;
            this.required = required;
        }

        public static EidasAttribute fromString(String str) {
            for (EidasAttribute b : EidasAttribute.values()) {
                if (b.friendlyName.equalsIgnoreCase(str)) {
                    return b;
                }
            }
            throw new IllegalArgumentException("No constant with friendlyName '" + str + "' found");
        }

        public String getFriendlyName() {
            return friendlyName;
        }

        public String getName() {
            return name;
        }

        public boolean isRequired() {
            return required;
        }
    }