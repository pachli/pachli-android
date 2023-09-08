# Contributing translations

> [!NOTE]
> These instructions are supposed to be complete and accurate. If anything is unclear or does not work please report it so it can be fixed.

## Synopsis

Thank you for wanting to improve Pachli for others by making new translations or modifying existing ones.

After reading this document you will know:

- How the project uses Weblate to manage translations
- How to make your first translation change using Weblate
- How to use Weblate to suggest a translation to a new language
- Guidelines to follow when translating text

## Overview

The two types of text that need translating are:

1. Messages in the UI; menu items, confirmation messages, dialog boxes, things like that
2. The project description and release notes

Both are managed using [Weblate](https://weblate.org), which calls them "Components".

Using Weblate you can:

- See what text still needs to be translated in to a language you are interested in
- Submit a new translation of that text in to your language

## Sign the contributor license agreement

Please agree to the contributor license agreement before submitting any translations.

If you have already signed the CLA because you previously submitted a PR there is nothing more to do.

Otherwise, please visit https://cla-assistant.io/pachli/pachli-android, read, and sign the agreement using your GitHub account.

## Signing in to Weblate

Open https://hosted.weblate.org/projects/pachli/ and sign in.

If you do not already have an account you will need to register first. You can also sign in using an existing third-party account, such as a Google or GitHub account.

## Choosing a component

After signing in you should see a screen with at least two "Components"

Choose either:

- "UI strings" to change the text that appears in the application
- "Fastlane Metadata" to change the description and release notes

This should load a table of active languages the component is being translated to, as well as some statistics for each language.

## Contributing to an existing translation

Select the entry for the language you want to contribute to.

This should open the primary translation UI.

Using this UI you can:

- Step through the text, both translated and untranslated
- Suggest modifications to existing translations
- Suggest a new translation for untranslated text

Please read the [Weblate Translation Workflow](https://docs.weblate.org/en/latest/user/translating.html) documentation for specific instructions on how to use this interface.

## Starting a new translation

Click the "+" button at the top left of the first table row.

This will prompt you to select the new language.

It will be added to the list, and you can start submitting translation suggestions.

## Submitting changes

Using Weblate means that you do not directly submit pull requests to the project. Weblate batches up changes and submits them on your behalf.

The changes you make will be attributed to your account.

## Guidelines for translations

- Please use gender-neutral language
- Address the user informally (e.g., in German "du", not "Sie")
