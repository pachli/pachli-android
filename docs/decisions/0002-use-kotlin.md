# Use Kotlin

## Context and Problem Statement

Android applications can be written in Java, Kotlin, or a mix of the two. What should the project choose?

## Considered Options

* Use Kotlin for all new code, migrate existing code
* Allow new code to be Kotlin or Java
* Use Java for all new code, migrate existing code

## Decision Outcome

Chosen option: "Use Kotlin for all new code, migrate existing code", because comes out best (see below).

### Consequences

* Good, because it simplifies the number of languages contributors need to know
* Good, because Kotlin has features that mitigate issues that require third party libraries in Java (null pointer exceptions, immutable collections, etc)
* Good, because Kotlin's support for structured concurrency resolves an entire class of bugs
* Good, because Android ecosystem, especially Google libraries, is increasingly moving to Kotlin. Google describes Kotlin as part of [Modern Android Development](https://developer.android.com/modern-android-development)
* Bad, because contributing to third party libraries used by the app may still require Java
* Bad, because Kotlin may be unfamiliar to potential contributors
* Neutral, because tools exist to automatically convert Java to equivalent (but possibly not idiomatic) Kotlin

## Pros and Cons of the Options

### Use Kotlin for all new code, migrate existing code

* Good, because the number of languages contributors need to know remains low
* Good, because Kotlin has features that mitigate issues that require third party libraries in Java (null pointer exceptions, immutable collections, etc)
* Good, because Kotlin's support for structured concurrency resolves an entire class of bugs
* Good, because Android ecosystem, especially Google libraries, is increasingly moving to Kotlin. Google describes Kotlin as part of [Modern Android Development](https://developer.android.com/modern-android-development)
* Bad, because contributing to third party libraries used by the app may still require Java
* Bad, because Kotlin may be unfamiliar to potential contributors
* Neutral, because tools exist to automatically convert Java to equivalent (but possibly not idiomatic) Kotlin

### Allow new code to be Kotlin or Java

* Good, because it may be easier to attract new contributors already familiar with Java
* Bad, because a single PR may need to modify code in both languages, so contributors likely still need to be familiar with both
* Bad, because anyone conducting PR reviews must be familiar with both languages
* Bad, because although Java and Kotlin can interop it requires additional annotations and sometimes code on the Kotlin side, making the code more complicated

### Use Java for all new code, migrate existing code

* Bad, because this is the opposite direction the Android ecosystem is moving in
* Bad, because Kotlin brings significant improvements to Java that would be thrown out
* Bad, because additional work
