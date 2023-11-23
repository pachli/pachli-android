# Pachli changelog

## v2.0.0

### New features and other improvements

- Translate statuses on cached timelines (#[220](https://github.com/pachli/pachli-android/pull/220), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Display compose button when viewing hashtags (#[254](https://github.com/pachli/pachli-android/pull/254), [sanao](https://github.com/pachli/pachli-android/commits?author=naosak1006@gmail.com))
- Display compose button when viewing favorites list (#[276](https://github.com/pachli/pachli-android/pull/276), [sanao](https://github.com/pachli/pachli-android/commits?author=naosak1006@gmail.com))
- Increase the amount of information in link previews (#[275](https://github.com/pachli/pachli-android/pull/275), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Warn the user about an Android animation bug (#[274](https://github.com/pachli/pachli-android/pull/274), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

### Significant bug fixes

- Prevent a ClassCastException when handling timeline errors (#[273](https://github.com/pachli/pachli-android/pull/273), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Use transparent system bar on all Android versions (#[272](https://github.com/pachli/pachli-android/pull/272), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Ensure update-related data classes are retained (#[269](https://github.com/pachli/pachli-android/pull/269), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Update themes to correct poll/black theme issues (#[255](https://github.com/pachli/pachli-android/pull/255), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

### Translations

- Update German translations ([vinz](https://github.com/pachli/pachli-android/commits?author=vinz@vinzv.de))
- Update Japanese translations ([sanao](https://github.com/pachli/pachli-android/commits?author=jinsei@sanao.dev))
- Update Italian translations ([chaoscalm](https://github.com/pachli/pachli-android/commits?author=g.ammazzini@gmail.com))

## v1.4.0

### New features and other improvements

- Periodically check for updates and alert user (#[236](https://github.com/pachli/pachli-android/pull/236))

### Significant bug fixes

- Show preference changes immediately (#[234](https://github.com/pachli/pachli-android/pull/234))
- Honour the FAB_HIDE preference in timelines (#[227](https://github.com/pachli/pachli-android/pull/227))
- Ensure system controls are visible on older devices (#[224](https://github.com/pachli/pachli-android/pull/224))
- Remove progressbar from status timelines (#[208](https://github.com/pachli/pachli-android/pull/208))
- Restore image aspect ratio when drag ends (#[207](https://github.com/pachli/pachli-android/pull/207))
- Prevent potential crash when filters are slow to load (#[205](https://github.com/pachli/pachli-android/pull/205))

### Translations

- Update Italian translations (#[240](https://github.com/pachli/pachli-android/pull/240))

## v1.3.0

### New features and other improvements

-  Add a preference to hide "self-boosts" (#[193](https://github.com/pachli/pachli-android/pull/193))
-  Show announcement dates (#[151](https://github.com/pachli/pachli-android/pull/151))
-  Show full display and username in profile view (#[195](https://github.com/pachli/pachli-android/pull/195))
-  Improve display of "obscured" links (#[194](https://github.com/pachli/pachli-android/pull/194))
-  Adjust video playback UX behaviour (#[186](https://github.com/pachli/pachli-android/pull/186))

### Significant bug fixes

-  Ensure empty timeline message is visible (#[191](https://github.com/pachli/pachli-android/pull/191))
-  Use Pachli blue and orange for notification icons (#[182](https://github.com/pachli/pachli-android/pull/182))
-  Update timeline when link preview setting changes (#[180](https://github.com/pachli/pachli-android/pull/180))
-  Show media when resuming ViewMediaFragment (#[174](https://github.com/pachli/pachli-android/pull/174))
-  Show thread indicators and other dividers using Material colours (#[157](https://github.com/pachli/pachli-android/pull/157))

### Translations

-  Update Polish and Japanese translations

## v1.2.2

### New features and other improvements

-  Embed the privacy policy in the app (#[139](https://github.com/pachli/pachli-android/pull/139))

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
