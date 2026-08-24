---
paths:
  - "build.gradle"
  - "settings.gradle"
  - "gradle.properties"
  - "gradle/**"
---

# Gradle 9 conventions

- Bump the wrapper deliberately (`./gradlew wrapper --gradle-version=<version>`) rather than letting an IDE drift onto whatever Gradle is installed locally; verify plugin compatibility before moving off 9.7.1.
- `validateDistributionUrl=true` is already set in `gradle-wrapper.properties` — keep it. It stops a tampered wrapper script from silently downloading the wrong distribution.
- Configuration cache is stable in Gradle 9 (no longer incubating) and enabled here via `org.gradle.configuration-cache=true` in `gradle.properties`, alongside `org.gradle.caching=true` and `org.gradle.parallel=true`. The `org.sonarqube` plugin has supported the configuration cache since 7.2.0 (this project pins 7.4.0.8496), so `./gradlew build sonar` stays compatible.
- Once dependencies grow past what the Spring BOM manages, move to a version catalog (`gradle/libs.versions.toml`) instead of scattering literal version strings through `build.gradle`.
- Same GA-only rule as the Spring BOM: don't add a Gradle plugin at a `-milestone-`/`-rc-` coordinate when a stable release covers the need.
