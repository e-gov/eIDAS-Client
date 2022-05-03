#!/bin/sh

testConfDestination="eidas-client-webapp/target/generated-test-conf"

confFileName="application.properties"
keystoreFileName="samlKeystore.jks"
defaultHostname="http://localhost:8889"

if [ -z "$1" ]
then
      echo "Please specify a password for the TEST keystore and keys:"
      read password
      if [ -z "$password" ]
      then
        echo "Password must not be empty!"
        exit 1
      fi
fi

if [ -z "$2" ]
then
      echo "Please specify your hostname and port that will run eidas-client [$defaultHostname]:"
      read hostname
      if [ -z "$hostname" ]
      then
        hostname="$defaultHostname"
      fi
      echo $hostname will be used for constructing metadata and callback urls
fi

remove_file_if_exists() {
	if [ -e $1 ]
	then
		rm $1
		echo "File `readlink -f $1` already exists. Removing"
	fi
}

mkdir -p $testConfDestination
cd $testConfDestination

echo
echo "---------- [ Generating a sample keystore file with test keys]"
remove_file_if_exists $keystoreFileName


keytool -genkeypair -keyalg EC -keystore $keystoreFileName -keysize 384 -alias metadata -dname "CN=SP-metada-signing, OU=test, O=test, C=EE" -validity 730 -storepass $password -keypass $password || { echo 'Could not generate the keystore' ; exit 1; }
keytool -export -alias metadata -file sp_metadata.crt -keystore $keystoreFileName  -storepass $password || { echo 'Could not export SP metadata certificate' ; exit 1; }
keytool -genkeypair -keyalg EC -keystore $keystoreFileName -keysize 384 -alias requestsigning -dname "CN=SP-auth-request-signing, OU=test, O=test, C=EE" -validity 730 -storepass $password -keypass $password || { echo 'Could not generate the keypair for signing' ; exit 1; }
keytool -genkeypair -keyalg RSA -keystore $keystoreFileName -keysize 4096 -alias responseencryption -dname "CN=SP-response-encryption, OU=test, O=test, C=EE" -validity 730 -storepass $password -keypass $password || { echo 'Could not generate the keypair for encryption' ; exit 1; }
keytool -importcert -keystore $keystoreFileName -storepass $password -file ../../src/test/resources/scripts/eidastest.eesti.ee.pem -alias idpmetadata -noprompt || { echo 'Could not import the trust anchor' ; exit 1; }

echo
echo
echo "---------- [ Generating a sample $confFileName]"
remove_file_if_exists $confFileName

echo "eidas.client.keystore = file:`pwd`/$keystoreFileName">>$confFileName
echo "eidas.client.keystore-pass = $password">>$confFileName
echo "">>$confFileName

echo "eidas.client.metadata-signing-key-id = metadata">>$confFileName
echo "eidas.client.metadata-signing-key-pass = $password">>$confFileName
echo "eidas.client.metadata-signature-algorithm = http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512">>$confFileName
echo "">>$confFileName

echo "eidas.client.request-signing-key-id = requestsigning">>$confFileName
echo "eidas.client.request-signing-key-pass = $password">>$confFileName
echo "eidas.client.request-signature-algorithm = http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512">>$confFileName
echo "">>$confFileName

echo "eidas.client.response-decryption-key-id = responseencryption">>$confFileName
echo "eidas.client.response-decryption-key-pass = $password">>$confFileName
echo "">>$confFileName

echo "eidas.client.provider-name = DEMO SP">>$confFileName
echo "eidas.client.idp-metadata-url = https://eidastest.eesti.ee/EidasNode/ConnectorResponderMetadata">>$confFileName
echo "eidas.client.sp-entity-id = $2/metadata">>$confFileName
echo "eidas.client.callback-url = $2/returnUrl">>$confFileName
echo "">>$confFileName

echo "eidas.client.available-countries-public-fallback = EE,CA">>$confFileName
echo "eidas.client.available-countries-private = IT">>$confFileName

echo "eidas.client.idp-metadata-signing-certificate-key-id = idpmetadata">>$confFileName

echo "Please review the generated sample configuration: `readlink -f $confFileName`"


