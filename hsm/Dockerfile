FROM e-gov/eidas-client:latest

# Setup SoftHSM
RUN apt-get update && apt-get install -y softhsm2 opensc

# Setup tokens
COPY setup-tokens.sh credentials/ /opt/credentials/
RUN rm -rf /var/lib/softhsm/tokens \
    && mkdir -p /var/lib/softhsm/tokens \
    && chmod a+x /opt/credentials/setup-tokens.sh \
    && /opt/credentials/setup-tokens.sh \
    && chmod -R a+rwx /var/lib/softhsm \
    && chmod a+rx /etc/softhsm \
    && chmod a+r /etc/softhsm/*

WORKDIR /application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.JarLauncher"]