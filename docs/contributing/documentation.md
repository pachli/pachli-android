# Contributing to documentation

> [!NOTE]
> These instructions are supposed to be complete and accurate. If anything is unclear or does not work please report it so it can be fixed.

## Synopsis

Thank you for wanting to improve Pachli by contributing documentation skills.

After reading this document you will know:

- Specific areas to focus on that would bring immediate benefit

## Overview and activities

### Writing contributor documentation

[pachli-android/docs](https://github.com/pachli/pachli-android/tree/main/docs) contains the documentation for project contributors (you're reading some of it now).

This changes slowly over time, but may be incomplete or out of date.

These documents are generally either [How-to guides](https://diataxis.fr/how-to-guides/) or [explanations](https://diataxis.fr/explanation/).

### Writing in-app documentation

The app contains some "documentation", which will grow over time.

Most of this is short messages, labels in the UI, brief descriptions of preference settings, and so on.

However, some of it is longer text, and the amount of that will increase as the UI changes to show more hints on first-use to new users.

### Writing website content

https://pachli.app is the project's web presence. Its contents are a mix of:

- [How-to guides](https://diataxis.fr/how-to-guides/), such as https://pachli.app/download/
- [Explanations](https://diataxis.fr/explanation/), such as https://pachli.app/about/
- Blog posts
  - One for each release
  - Others as necessary

The website repository is https://github.com/pachli/website

## Technology

**Contributor documentation** is generally written in [GitHub flavoured Markdown](https://github.github.com/gfm/).

**In-app documentation** is generally written as [Android resource strings](https://developer.android.com/guide/topics/resources/string-resource). E.g., see [app/src/main/res/values/strings.xml](https://github.com/pachli/pachli-android/blob/main/app/src/main/res/values/strings.xml). Familiarity with Android resource strings (e.g., how placeholders are written, how to manage strings with different plural forms) is beneficial, but not mandatory.

**Website content** is managed using [Jekyll](https://jekyllrb.com/) and written in [GitHub flavoured Markdown](https://github.github.com/gfm/).
