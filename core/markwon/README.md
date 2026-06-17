# Markwon module

Markwon uses the Prism library, which has a non-incremental `kapt` step
during the build, logging:

```
The following annotation processors are not incremental: prism4j-bundler-2.0.0.jar (io.noties:prism4j-bundler:2.0.0).
Make sure all annotation processors are incremental to improve your build speed.
```

This disables incremental compilation for any module it's in.

This module holds just the code to initialise Markwon, so `core.ui`
can depend on it without disabling incremental compilation of the
`core.ui` module.
