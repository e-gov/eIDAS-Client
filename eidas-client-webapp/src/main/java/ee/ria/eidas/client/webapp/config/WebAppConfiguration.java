package ee.ria.eidas.client.webapp.config;

import ee.ria.eidas.client.AssertionConsumerServlet;
import ee.ria.eidas.client.authnrequest.EidasAuthenticationFilter;
import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.config.EidasClientProperties;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(EidasClientConfiguration.class)
public class WebAppConfiguration {

    @Autowired
    private EidasClientProperties eidasClientProperties;

    @Bean
    public FilterRegistrationBean eidasAuthProtectedUrlFilterRegistration(@Qualifier("authnReqSigningCredential") Credential signingCredential) {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setFilter(new EidasAuthenticationFilter(signingCredential, eidasClientProperties));
        registration.addUrlPatterns(eidasClientProperties.getLoginUrl());
        registration.setName("eIDAS Authentication Filter");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public ServletRegistrationBean eidasAuthAssertionConsumerServlet(ExplicitKeySignatureTrustEngine explicitKeySignatureTrustEngine, @Qualifier("responseAssertionDecryptionCredential") Credential responseAssertionDecryptionCredential) {
        ServletRegistrationBean bean = new ServletRegistrationBean(
                new AssertionConsumerServlet(eidasClientProperties, explicitKeySignatureTrustEngine, responseAssertionDecryptionCredential), eidasClientProperties.getSamlAssertionConsumerUrl());
        bean.setLoadOnStartup(1);
        return bean;
    }

}
