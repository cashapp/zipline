# Releasing

1. Update the `VERSION_NAME` in `gradle.properties` to the release version.

2. Update the `CHANGELOG.md`:
   1. Change the `Unreleased` header to the release version.
   2. Add a link URL to ensure the header link works.
   3. Add a new `Unreleased` section to the top.

3. Update the `README.md` so the "Download" section reflects the new release version and the
   snapshot section reflects the next "SNAPSHOT" version.

4. Commit

   ```
   $ git commit -am "Prepare version X.Y.X"
   ```

5. Publish

    ```
    $ ./gradlew clean publish
    ```

6. Visit [Sonatype Nexus](https://oss.sonatype.org/) and promote the artifact.

   If this step fails, drop the Sonatype repo, fix, commit, and publish again.

7. Tag

   ```
   $ git tag -am "Version X.Y.Z" X.Y.Z
   ```

8. Update the `VERSION_NAME` in `gradle.properties` to the next "SNAPSHOT" version.

9. Commit

   ```
   $ git commit -am "Prepare next development version"
   ```

10. Push!

   ```
   $ git push && git push --tags
   ```
