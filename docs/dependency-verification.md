# Dependency verifcation

Gradle [dependency verification]((https://docs.gradle.org/current/userguide/dependency_verification.html)) is enabled to verify SHA-256 checksums and PGP keys when dependencies are updated.

Checksums and keys are stored in the `gradle/verification-metadata.xml` and `gradle/verification-keyring.keys` files.

Checksums and keys are verified early in the build process. Any failures will stop CI, and the error will be reported in the log.

## Updating existing dependencies

If the update PR is created by [Renovate](https://www.mend.io/renovate/) it should include the updated checksum automatically ([Automated dependency updates for Gradle](https://docs.renovatebot.com/modules/manager/gradle/)).

If manually updating a dependency then regenerate the keys and checksums with:

```shell
./gradlew --refresh-keys --write-verification-metadata pgp,sha256 \
    --export-keys bundle installDist
```

The `bundle` and `installDist` tasks together should depend on every third party dependency, so ensures the metadata is generated.

In both cases verify the checksum and keys against published metadata before approving the PR.
