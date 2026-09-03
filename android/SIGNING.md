# DoTi release signing

DoTi production releases use one long-lived signing key. The private key and
password file must remain outside this Git repository.

## Public certificate identity

- Alias: `doti-release`
- Algorithm: RSA 4096 / SHA256withRSA
- SHA-256: `D3:DB:71:99:CB:E4:DE:46:28:22:A3:8F:6E:B5:7A:F3:35:D3:99:95:AE:05:5C:3C:9E:80:03:3D:B2:ED:84:79`

## Local configuration

Set `DOTI_SIGNING_PROPERTIES` in the ignored `android/local.properties` file,
or pass it as a Gradle property. The referenced properties file must contain:

```properties
storeFile=/secure/path/to/doti-release.jks
storePassword=stored-outside-git
keyAlias=doti-release
keyPassword=stored-outside-git
```

Back up the keystore and its password in a secure location. Losing either one
prevents future APK updates from being signed as the same Android application.
