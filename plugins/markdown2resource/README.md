# markdown2resource-plugin

## Synopsis

Gradle plugin to convert one or more Markdown files to Java files with static constants where the Markdown has been converted to HTML. Similar (but not quite identical) to Android resources.

## Example

In `build.gradle`:

```groovy
// Install the plugin
plugins {
    id "app.pachli.plugins.markdown2resource"
}

// ...

// Configure the files to be processed
markdown2resource {
    files = [ layout.projectDirectory.file('../PRIVACY.md') ]
}
```

In code:

```kotlin
// Assume binding.privacyPolicy references a `WebView`

// The generated string constant is in the `markdownR.html` package, named
// `PRIVACY_md`. To load the content in to a WebView it must be converted to
// base64
val html = Base64.encodeToString(markdownR.html.PRIVACY_md.toByteArray(), Base64.NO_PADDING)
binding.privacyPolicy.loadData(html, "text/html", "base64")
```

## Configuration

The `markdown2resource` block supports the following options.

`files` - a list of `RegularFile` in Markdown.

`packageName` - the package name to use for the generated resources. Default is the `android.namespace` of the build variant.

`resourceClassName` - the outer class name to use for the generated resources. Default is `markdownR`.

`stringClassName` - the inner class name to use for the generated resources. Default is `html`.

## Tasks

The plugin creates N tasks, one for each configured build variant, named `markdown2resource${variant.name.capitalized()}`

For example:

1. You have defined `debug` and `release` build types, two tasks will be created, `markdown2resourceDebug` and `markdown2resourceRelease`.
2. You have defined `debug` and `release` build types and `demo` and `full` product flavours, four tasks will be created, `markdown2resourceDemoDebug`, `markdown2resourceDemoRelease`, `markdown2resourceFullDebug`, `markdown2resourceFullRelease`.
