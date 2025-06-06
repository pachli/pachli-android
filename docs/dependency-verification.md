# Dependency verifcation

Gradle [dependency verification]((https://docs.gradle.org/current/userguide/dependency_verification.html)) is enabled to verify SHA-256 checksums and PGP keys when dependencies are updated.

> Note: Currently only checksums are verified. Some PGP keys are stored, but triggers verification issues and problems with Renovate.
>
> Renovate discussion: https://github.com/renovatebot/renovate/discussions/36208

Checksums and keys are stored in the `gradle/verification-metadata.xml` and `gradle/verification-keyring.keys` files.

Checksums and keys are verified early in the build process. Any failures will stop CI, and the error will be reported in the log.

## Updating existing dependencies

If the update PR is created by [Renovate](https://www.mend.io/renovate/) it should include the updated checksum and/or keys automatically ([Automated dependency updates for Gradle](https://docs.renovatebot.com/modules/manager/gradle/)).

If manually updating a dependency then Gradle can regenerate the keys and checksums.

Until https://github.com/gradle/gradle/issues/19228 is fixed this must be done with an empty `$GRADLE_USER_HOME` directory, otherwise previously cached dependencies may not be processed.

```shell
export TMP_GRADLE_USER_HOME=$(mktemp)
GRADLE_USER_HOME=${TMP_GRADLE_USER_HOME} ./gradlew \
    --refresh-keys \
    --write-verification-metadata pgp,sha256 \
    --export-keys \
    --no-daemon \
    test installDist
rm -rf ${TMP_GRADLE_USER_HOME}
```

The `test` and `installDist` tasks together should depend on every third party dependency, so ensures the metadata is generated.

In both cases verify the checksum and keys against published metadata before approving the PR.
