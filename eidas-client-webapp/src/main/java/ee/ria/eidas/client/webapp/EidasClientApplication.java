package ee.ria.eidas.client.webapp;

import co.elastic.apm.attach.ElasticApmAttacher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("ee.ria.eidas.client")
public class EidasClientApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        ElasticApmAttacher.attach();
        return application.sources(EidasClientApplication.class);
    }

    public static void main(String[] args) {
        ElasticApmAttacher.attach();
        SpringApplication.run(EidasClientApplication.class, args);
    }

}
