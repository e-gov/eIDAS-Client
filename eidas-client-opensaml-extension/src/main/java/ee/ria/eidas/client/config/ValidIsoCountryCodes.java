package ee.ria.eidas.client.config;

import org.apache.commons.collections.CollectionUtils;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Documented
@Constraint(validatedBy = CountryCodeValidator.class)
@Target( { ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIsoCountryCodes {
    String message() default "Invalid country list";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

class CountryCodeValidator implements ConstraintValidator<ValidIsoCountryCodes, List<String>> {

    @Override
    public void initialize(ValidIsoCountryCodes constraint) {}

    @Override
    public boolean isValid(final List<String> value, final ConstraintValidatorContext constraintContext) {
        Collection<String> invalidCountryCodes = CollectionUtils.subtract(value, Arrays.asList(Locale.getISOCountries()));
        if ( !invalidCountryCodes.isEmpty() ) {
            constraintContext.disableDefaultConstraintViolation();
            constraintContext.buildConstraintViolationWithTemplate(
                    "The following values are not valid ISO 3166-1 alpha-2 codes: " + invalidCountryCodes
            ).addConstraintViolation();
            return false;
        } else {
            return true;
        }
    }
}