package ee.ria.eidas.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Properties;

//This class was implemented to prevent the need for mvn compile when running tests in IDE
@TestConfiguration
public class MockPropertyBeanConfiguration {

    @Bean
    @ConditionalOnMissingBean(BuildProperties.class)
    public BuildProperties dummyBuildProperties() {
        Properties properties = new Properties();
        properties.put("group", "group-value");
        properties.put("artifact", "artifact-value");
        properties.put("name", "name-value");
        properties.put("version", "version-value");
        properties.put("time", "time-value");
        return new BuildProperties(properties);
    }

    @Bean
    @ConditionalOnMissingBean(GitProperties.class)
    public GitProperties dummyGitProperties() {
        Properties properties = new Properties();
        properties.put("branch", "branch-value");
        properties.put("commit.id", "commit.id-value");
        properties.put("commit.id.abbrev", "commit.id.abbrev-value");
        properties.put("commit.time", "commit.time-value");
        return new GitProperties(properties);
    }
}
