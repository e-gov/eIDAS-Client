#!/bin/bash

MODULE=/usr/lib/softhsm/libsofthsm2.so
CRED_PATH=/opt/credentials
PIN=1234

pkcs11-tool --module $MODULE --slot 0 --init-token --so-pin 0000 --init-pin --pin $PIN --label EIDAS

pkcs11-tool --module $MODULE --token-label EIDAS \
  --login --pin $PIN \
  --write-object $CRED_PATH/metadata_sign_ec.key.pem \
  --type privkey --label metadata-sign-ec --id 6d657461646174612d7369676e2d6563 \
  --usage-sign

pkcs11-tool --module $MODULE --token-label EIDAS \
  --login --pin $PIN \
  --write-object $CRED_PATH/metadata_sign_ec.cer.pem \
  --type cert --label metadata-sign-ec --id 6d657461646174612d7369676e2d6563

pkcs11-tool --module $MODULE --token-label EIDAS \
  --login --pin $PIN \
  --write-object $CRED_PATH/metadata_sign_rsa.key.pem \
  --type privkey --label metadata-sign-rsa --id 6d657461646174612d7369676e2d727361 \
  --usage-sign

pkcs11-tool --module $MODULE --token-label EIDAS \
  --login --pin $PIN \
  --write-object $CRED_PATH/metadata_sign_rsa.cer.pem \
  --type cert --label metadata-sign-rsa --id 6d657461646174612d7369676e2d727361

pkcs11-tool --module $MODULE --token-label EIDAS \
  --login --pin $PIN \
  --write-object $CRED_PATH/authn_sign_ec.key.pem \
  --type privkey --label authn-sign-ec --id 617574686e2d7369676e2d6563 \
  --usage-sign

pkcs11-tool --module $MODULE --token-label EIDAS \
  --login --pin $PIN \
  --write-object $CRED_PATH/authn_sign_ec.cer.pem \
  --type cert --label authn-sign-ec --id 617574686e2d7369676e2d6563

pkcs11-tool --module $MODULE --token-label EIDAS \
  --login --pin $PIN \
  --write-object $CRED_PATH/authn_sign_rsa.key.pem \
  --type privkey --label authn-sign-rsa --id 617574686e2d7369676e2d727361 \
  --usage-sign

pkcs11-tool --module $MODULE --token-label EIDAS \
  --login --pin $PIN \
  --write-object $CRED_PATH/authn_sign_rsa.cer.pem \
  --type cert --label authn-sign-rsa --id 617574686e2d7369676e2d727361

pkcs11-tool --module $MODULE --token-label EIDAS \
  --login --pin $PIN \
  --write-object $CRED_PATH/response_decryption.key.pem \
  --type privkey --label response-decryption --id 726573706f6e73652d64656372797074696f6e \
  --usage-decrypt

pkcs11-tool --module $MODULE --token-label EIDAS \
  --login --pin $PIN \
  --write-object $CRED_PATH/response_decryption.cer.pem \
  --type cert --label response-decryption --id 726573706f6e73652d64656372797074696f6e
