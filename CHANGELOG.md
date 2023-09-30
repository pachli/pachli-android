# Pachli changelog

## v1.2.1

### New features and other improvements

-  Link to the privacy policy from "About" (#[137](https://github.com/pachli/pachli-android/pull/137))

## v1.2

### New features and other improvements

-  Show vertical scrollbars on scrollable lists (#[96](https://github.com/pachli/pachli-android/pull/96))

### Significant bug fixes

-  Restore the user's reading position under all circumstances (#[133](https://github.com/pachli/pachli-android/pull/133))
-  Enable/disable vote button when the user can/can't vote (#[91](https://github.com/pachli/pachli-android/pull/91))
-  Use the correct colour for the status bar
-  Ensure poll option text is visible against the background (#[86](https://github.com/pachli/pachli-android/pull/86))

## v1.1

### Significant bug fixes

-  Use the correct contrast colour on hashtag tab chips (#[47](https://github.com/pachli/pachli-android/pull/47))
-  Scale the monochrome icon to fit (#[46](https://github.com/pachli/pachli-android/pull/46))
-  Restore the user's home timeline position (#[44](https://github.com/pachli/pachli-android/pull/44))
-  Ensure refreshing does not create a gap in the timeline (#[43](https://github.com/pachli/pachli-android/pull/43))
-  Show the FAB according to the user's preferences (#[29](https://github.com/pachli/pachli-android/pull/29))
-  Fix toolbar flickering when scrolling lists (#[26](https://github.com/pachli/pachli-android/pull/26))
-  Fix crash on entering MainActivity on Pixel C devices (#[25](https://github.com/pachli/pachli-android/pull/25))

## v1.0

Pachli 1.0 is a fork of [Tusky 23.0](https://github.com/tuskyapp/Tusky/releases/tag/v23.0). The changes listed here are relative to that version.

### New features and other improvements

- "Load more" no more, posts load as you scroll
- Support trending links and posts
- Support more than 5 tabs at once
- Choose from a range of accessible fonts
- New media player component (more video formats supported)
- Mark lists as "exclusive". Posts from accounts on exclusive lists only appear in the list, not your home timeline
- Add "bookmarks" to a tab
- Extend the list of available languages

### Significant bug fixes

- Images don't "stick" when you zoom or swipe between them
- Keep search text when moving between search tabs
- Show the actual error message when an image upload fails
- Filters work in notifications

### Significant UI changes

- A visual refresh, new colour scheme, some new UX components
- Determine the default theme from your Android settings
- Move "Clear notifications" to a menu to make it more difficult to trigger accidentally
