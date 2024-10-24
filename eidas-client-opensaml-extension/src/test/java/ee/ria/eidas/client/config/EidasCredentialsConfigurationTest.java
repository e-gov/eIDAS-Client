package ee.ria.eidas.client.config;


import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.validation.beanvalidation.SpringConstraintValidatorFactory;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@TestPropertySource(locations = "classpath:application-test-hazelcast-disabled.properties")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(
        classes = {EidasClientConfiguration.class, EidasCredentialsConfiguration.class},
        initializers = ConfigDataApplicationContextInitializer.class)
public class EidasCredentialsConfigurationTest {

    @Autowired
    EidasClientProperties eidasClientProperties;

    @Autowired
    AutowireCapableBeanFactory autowireCapableBeanFactory;

    private Validator validator;

    @PostConstruct
    void setupValidator() {
        ValidatorFactory validatorFactory = Validation.byDefaultProvider()
                .configure()
                .constraintValidatorFactory(new SpringConstraintValidatorFactory(autowireCapableBeanFactory))
                .buildValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @Test
    public void validationFailsWhen_HsmNotEnabled_AndKeyPasswordNotSet() {
        eidasClientProperties.getHsm().setEnabled(false);
        eidasClientProperties.setMetadataSigningKeyPass(null);
        ConstraintViolation<EidasClientProperties> constraintViolation = validate();
        assertEquals("Following properties are required if HSM is disabled: eidas.client.metadata_signing_key_pass, eidas.client.request_signing_key_pass, eidas.client.response_decryption_key_pass", constraintViolation.getMessage());
    }

    @Test
    public void validationFailsWhen_HsmEnabled_AndPinNotSet() {
        eidasClientProperties.getHsm().setEnabled(true);
        eidasClientProperties.getHsm().setLibrary("/");
        eidasClientProperties.getHsm().setPin(null);
        eidasClientProperties.getHsm().setSlot("0");
        eidasClientProperties.getHsm().setSlotListIndex(null);
        ConstraintViolation<EidasClientProperties> constraintViolation = validate();
        assertEquals("Invalid HSM configuration", constraintViolation.getMessage());
    }

    @Test
    public void validationFailsWhen_HsmEnabled_AndLibraryNotSet() {
        eidasClientProperties.getHsm().setEnabled(true);
        eidasClientProperties.getHsm().setLibrary(null);
        eidasClientProperties.getHsm().setPin("1234");
        eidasClientProperties.getHsm().setSlot("0");
        eidasClientProperties.getHsm().setSlotListIndex(null);
        ConstraintViolation<EidasClientProperties> constraintViolation = validate();
        assertEquals("Invalid HSM configuration", constraintViolation.getMessage());
    }

    @Test
    public void validationFailsWhen_HsmEnabled_AndSlotNotSetAndSlotIndexIsNotSet() {
        eidasClientProperties.getHsm().setEnabled(true);
        eidasClientProperties.getHsm().setLibrary("/");
        eidasClientProperties.getHsm().setPin("1234");
        eidasClientProperties.getHsm().setSlot(null);
        eidasClientProperties.getHsm().setSlotListIndex(null);
        ConstraintViolation<EidasClientProperties> constraintViolation = validate();
        assertEquals("Invalid HSM configuration", constraintViolation.getMessage());
    }

    @Test
    public void validationSucceedsWhen_HsmEnabled_AndSlotIsSet() {
        eidasClientProperties.getHsm().setEnabled(true);
        eidasClientProperties.getHsm().setLibrary("/");
        eidasClientProperties.getHsm().setPin("1234");
        eidasClientProperties.getHsm().setSlot("0");
        eidasClientProperties.getHsm().setSlotListIndex(null);
        assertNoValidationErrors();
    }

    @Test
    public void validationSucceedsWhen_HsmEnabled_AndSlotListIndexIsSet() {
        eidasClientProperties.getHsm().setEnabled(true);
        eidasClientProperties.getHsm().setLibrary("/");
        eidasClientProperties.getHsm().setPin("1234");
        eidasClientProperties.getHsm().setSlot(null);
        eidasClientProperties.getHsm().setSlotListIndex(0);
        assertNoValidationErrors();
    }

    private void assertNoValidationErrors() {
        Set<ConstraintViolation<EidasClientProperties>> constraintViolations = validator.validate(eidasClientProperties);
        assertTrue(constraintViolations.isEmpty());
    }

    private ConstraintViolation<EidasClientProperties> validate() {
        Set<ConstraintViolation<EidasClientProperties>> constraintViolations = validator.validate(eidasClientProperties);
        assertEquals(1, constraintViolations.size());
        return constraintViolations.iterator().next();
    }
}
