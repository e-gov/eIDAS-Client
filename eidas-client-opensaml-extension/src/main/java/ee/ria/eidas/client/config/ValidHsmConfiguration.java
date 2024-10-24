package ee.ria.eidas.client.config;

import ee.ria.eidas.client.config.EidasClientProperties.HsmProperties;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.apache.logging.log4j.util.Strings.isEmpty;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {HsmConfigurationValidator.class})
public @interface ValidHsmConfiguration {
    String message() default "Invalid HSM configuration";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

class HsmConfigurationValidator implements ConstraintValidator<ValidHsmConfiguration, EidasClientProperties> {

    @Override
    public boolean isValid(EidasClientProperties eidasClientProperties, ConstraintValidatorContext context) {
        HsmProperties hsmProperties = eidasClientProperties.getHsm();
        boolean hsmDisabled = hsmProperties == null || !hsmProperties.isEnabled();
        if (hsmDisabled && (isEmpty(eidasClientProperties.getMetadataSigningKeyPass())
                || isEmpty(eidasClientProperties.getRequestSigningKeyPass()) || isEmpty(eidasClientProperties.getResponseDecryptionKeyPass()))) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Following properties are required if HSM is disabled: eidas.client.metadata_signing_key_pass, " +
                    "eidas.client.request_signing_key_pass, eidas.client.response_decryption_key_pass")
                    .addConstraintViolation();
            return false;
        } else return hsmDisabled
                || (!isEmpty(hsmProperties.getLibrary()) && !isEmpty(hsmProperties.getPin())
                && (hsmProperties.getSlotListIndex() != null || !isEmpty(hsmProperties.getSlot())));
    }
}
