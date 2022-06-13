Zipline Database
==============

Zipline Loader supports optional caching which as a dependency uses a SqlLite database to track cached file state to support download exceptions, cache expiry, and other capabilities.

To provide for easier multi-platform creation of ready-to-use databases, the Zipline Database package provides a complete interface and implementation which includes creating and running migrations on the database to make it ready-to-use.

Zipline uses [SqlDelight](https://cashapp.github.io/sqldelight/) to define Kotlin interfaces for interacting with SqlLite databases.
