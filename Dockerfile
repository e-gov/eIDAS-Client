FROM maven:3.6.3-jdk-8-slim as builder
WORKDIR application
COPY ./pom.xml ./pom.xml
COPY ./layers.xml ./layers.xml
COPY ./eidas-client-opensaml-extension/pom.xml ./eidas-client-opensaml-extension/pom.xml
COPY ./eidas-client-webapp/pom.xml ./eidas-client-webapp/pom.xml
RUN mvn dependency:go-offline -B

COPY ./eidas-client-opensaml-extension ./eidas-client-opensaml-extension
COPY ./eidas-client-webapp ./eidas-client-webapp
RUN mvn clean package -DskipTests=true -Djacoco.skip=true -P jar
RUN java -Djarmode=layertools -jar eidas-client-webapp/target/*.jar extract

FROM maven:3.6.3-jdk-8-slim
WORKDIR /application
COPY --from=builder application/dependencies/ ./
COPY --from=builder application/spring-boot-loader/ ./
COPY --from=builder application/internal-dependencies ./
COPY --from=builder application/snapshot-dependencies/ ./
COPY --from=builder application/application/ ./
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.JarLauncher"]