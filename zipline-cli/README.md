# zipline-cli

A CLI which exposes some Zipline functionality to non-Gradle projects.

For example, an iOS-Swift app could use the CLI to download Zipline code to include in the app release.

## Download

A zip of the tool is available on the [latest release](https://github.com/cashapp/zipline/releases/latest).
Once extracted, run either the `bin/zipline-cli` or `bin/zipline-cli.bat` file from the command line.

```
$ bin/zipline-cli -h
Usage: zipline-cli [-hV] COMMAND
Use Zipline without Gradle.
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
Commands:
  download  Recursively download Zipline code to a directory from a URL.
  help      Displays help information about the specified command
```

## Local development

When developing locally, the `installDist` task will create an exploded version of the zip in the `build/install/` directory.
This allows you to test the distribution without needing to constantly unzip an archive.

```
$ ./gradlew :zipline-cli:installDist

$ zipline-cli/build/install/zipline-cli/bin/zipline-cli -h
Usage: zipline-cli [-hV] COMMAND
Use Zipline without Gradle.
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
Commands:
  download           Recursively download Zipline code to a directory from a URL
  generate-key-pair  Generate an Ed25519 key pair for ManifestSigner and ManifestVerifier
  help               Displays help information about the specified command
```
