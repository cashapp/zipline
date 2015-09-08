Duktape Android
===============

```java
Duktape duktape = new Duktape();
try {
  System.out.println(duktape.evaluate("'hello world'.toUpperCase();"));
} finally {
  duktape.close();
}
```

## On Android

```
./gradlew build
```

## On a Mac

Build the duktape binary.

```
./build_mac
```

Set the `java.library.path` system property to `build/` when you execute Java.
