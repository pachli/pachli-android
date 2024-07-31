# Pachli changelog

## v2.7.1

### Significant bug fixes

- Correctly position preview card images, show card description (#[860](https://github.com/pachli/pachli-android/pull/860), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

## v2.7.0

### New features and other improvements

- Provide a UI to edit different search operators (#[829](https://github.com/pachli/pachli-android/pull/829), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Edit a matching filter directly from the timeline (#[819](https://github.com/pachli/pachli-android/pull/819), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Show a specific message if requests are rate-limited (#[818](https://github.com/pachli/pachli-android/pull/818), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Show autocomplete hashtags with usage counts, sort by popularity (#[807](https://github.com/pachli/pachli-android/pull/807), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Show author bylines on preview cards, tap to view profile (#[805](https://github.com/pachli/pachli-android/pull/805), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Provide more detail in errors, especially media upload errors (#[801](https://github.com/pachli/pachli-android/pull/801), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Warn the user if the posting language might be incorrect (#[792](https://github.com/pachli/pachli-android/pull/792), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

### Significant bug fixes

- Ensure trending tag name is fully visible (#[831](https://github.com/pachli/pachli-android/pull/831), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Support same capabilities on Glitch as Mastodon (#[828](https://github.com/pachli/pachli-android/pull/828), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Enable server filtering on GoToSocial 0.16+ (#[826](https://github.com/pachli/pachli-android/pull/826), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Use colorPrimary for checked/selected dialog items (#[825](https://github.com/pachli/pachli-android/pull/825), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Don't focus search query when returning to the results list (#[824](https://github.com/pachli/pachli-android/pull/824), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Ensure initial status length calculation includes the content warning (#[821](https://github.com/pachli/pachli-android/pull/821), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Correctly punctuate a status content description (#[808](https://github.com/pachli/pachli-android/pull/808), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

### Translations

- Update Chinese (Traditional) translations ([Ricky From Hong Kong](https://github.com/pachli/pachli-android/commits?author=lamricky11@hotmail.com))
- Update Chinese (Simplified) (zh_MO) translations ([Ricky From Hong Kong](https://github.com/pachli/pachli-android/commits?author=lamricky11@hotmail.com))
- Update Chinese (Traditional, Hong Kong) translations ([Ricky From Hong Kong](https://github.com/pachli/pachli-android/commits?author=lamricky11@hotmail.com))
- Update English (United Kingdom) translations ([Ricky From Hong Kong](https://github.com/pachli/pachli-android/commits?author=lamricky11@hotmail.com))
- Update Finnish translations ([Kalle Kniivilä](https://github.com/pachli/pachli-android/commits?author=kalle.kniivila@gmail.com))
- Update Irish translations ([Aindriú Mac Giolla Eoin](https://github.com/pachli/pachli-android/commits?author=aindriu80@gmail.com))
- Update Spanish translations ([Miles Krell](https://github.com/pachli/pachli-android/commits?author=noreply@mileskrell.com))
- Update Norwegian Bokmål translations ([sunniva](https://github.com/pachli/pachli-android/commits?author=schildkroteskoldpadda@gmail.com))
- Update Spanish translations ([Juan M Sevilla](https://github.com/pachli/pachli-android/commits?author=jumase@disroot.org))

## v2.6.0

### New features and other improvements

- Show "Suggested accounts" (#[734](https://github.com/pachli/pachli-android/pull/734), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Support server filters in GoToSocial 0.16.0 (#[765](https://github.com/pachli/pachli-android/pull/765), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

### Significant bug fixes

- Set font size and family when going back from Preferences (#[781](https://github.com/pachli/pachli-android/pull/781), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Correctly handle setting / editing a status' language (#[780](https://github.com/pachli/pachli-android/pull/780), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Improve readability of status media labels (#[778](https://github.com/pachli/pachli-android/pull/778), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Ensure text in nav menu account header is legible (#[773](https://github.com/pachli/pachli-android/pull/773), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Don't exceed the maximum number of created shortcuts (#[771](https://github.com/pachli/pachli-android/pull/771), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Use default "Navigate up" content description in ViewThreadActivity (#[764](https://github.com/pachli/pachli-android/pull/764), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Show/hide the list of lists appropriately when showing errors (#[740](https://github.com/pachli/pachli-android/pull/740), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

### Translations

- Update Finnish translations ([Kalle Kniivilä](https://github.com/pachli/pachli-android/commits?author=kalle.kniivila@gmail.com))
- Update Spanish translations ([Miles Krell](https://github.com/pachli/pachli-android/commits?author=noreply@mileskrell.com))
- Update Swedish translations ([bittin1ddc447d824349b2](https://github.com/pachli/pachli-android/commits?author=bittin@reimu.nl))

## v2.5.2

### New features and other improvements

- Check Hometown/Glitch server instanceinfo for translation support (#[718](https://github.com/pachli/pachli-android/pull/718), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

### Significant bug fixes

- Prevent crash on "hidden domains" page (#[703](https://github.com/pachli/pachli-android/pull/703), [Miles Krell](https://github.com/pachli/pachli-android/commits?author=16272279+mileskrell@users.noreply.github.com))
- Generate useful error messages for all errors (#[719](https://github.com/pachli/pachli-android/pull/719), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Only include transitionKind in intents for Pachli activities (#[716](https://github.com/pachli/pachli-android/pull/716), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Only suggest upgrading to a version F-Droid has built (#[717](https://github.com/pachli/pachli-android/pull/717), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Re-enable accessiblity actions in all timelines (#[715](https://github.com/pachli/pachli-android/pull/715), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Prompt user to save/discard changes after editing bio (#[678](https://github.com/pachli/pachli-android/pull/678), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Show edit history for status' with polls (#[672](https://github.com/pachli/pachli-android/pull/672), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

### Translations

- Update Spanish translations ([Miles Krell](https://github.com/pachli/pachli-android/commits?author=noreply@mileskrell.com))
- Update French translations ([Black_Eyes](https://github.com/pachli/pachli-android/commits?author=valentin@girod.fr))
- Update Finnish translations ([Kalle Kniivilä](https://github.com/pachli/pachli-android/commits?author=kalle.kniivila@gmail.com))

## v2.5.1

### Significant bug fixes

- Only enable accessible actions on notifications attached to a status ([Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

## v2.5.0

### New features and other improvements

- Update activity transitions, prepare for predictive-back (#[650](https://github.com/pachli/pachli-android/pull/650), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Use Let's Encrypt certificates on API 23 devices (#[640](https://github.com/pachli/pachli-android/pull/640), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Fetch more trending posts, links, and hashtags (#[634](https://github.com/pachli/pachli-android/pull/634), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Disable filters for accounts on servers running Sharkey (#[610](https://github.com/pachli/pachli-android/pull/610), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Show any errors when registering the app during login (#[594](https://github.com/pachli/pachli-android/pull/594), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Simplify adding/removing timelines from tabs (#[587](https://github.com/pachli/pachli-android/pull/587), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Add left-nav entries for Local, Federated, and Direct Messages (#[583](https://github.com/pachli/pachli-android/pull/583), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Support adding "Favourites" to a tab (#[578](https://github.com/pachli/pachli-android/pull/578), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

### Significant bug fixes

- Enable talkback actions in notifications and conversations (#[661](https://github.com/pachli/pachli-android/pull/661), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Improve transitions in/out of playing video (#[636](https://github.com/pachli/pachli-android/pull/636), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Update PageCache to handle non-chronological ordering (#[633](https://github.com/pachli/pachli-android/pull/633), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Set correct text direction for localised error messages  (#[601](https://github.com/pachli/pachli-android/pull/601), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Allow filter expiration to be set to "indefinite" (#[582](https://github.com/pachli/pachli-android/pull/582), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Create a dedicated menu for NotificationsActivity (#[571](https://github.com/pachli/pachli-android/pull/571), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

### Translations

- Update Finnish translations ([Kalle Kniivilä](https://github.com/pachli/pachli-android/commits?author=kalle.kniivila@gmail.com))
- Update Kabyle translations ([ButterflyOfFire](https://github.com/pachli/pachli-android/commits?author=boffire@users.noreply.hosted.weblate.org))
- Update Spanish translations ([Miles Krell](https://github.com/pachli/pachli-android/commits?author=noreply@mileskrell.com))

## v2.4.0

### New features and other improvements

- Notify the user about severed relationships (#[557](https://github.com/pachli/pachli-android/pull/557), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Show the post's language tag in the detailed view  (#[546](https://github.com/pachli/pachli-android/pull/546), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Allow user to see poll results before voting (#[543](https://github.com/pachli/pachli-android/pull/543), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Allow the user to set a list's replies policy (#[534](https://github.com/pachli/pachli-android/pull/534), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Show network activity in ListsActivity (#[533](https://github.com/pachli/pachli-android/pull/533), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Improve Image Viewer toolbar auto-hide (#[521](https://github.com/pachli/pachli-android/pull/521), [Angelo Suzuki](https://github.com/pachli/pachli-android/commits?author=1063155+tinsukE@users.noreply.github.com))
- Support client filters in GoToSocial 0.15.0 (#[523](https://github.com/pachli/pachli-android/pull/523), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Show "Followed hashtags" menu item in left-nav ([Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Tapping followed hashtag names in lists opens relevant timeline ([Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Show user's lists in the left-side navigation menu (#[514](https://github.com/pachli/pachli-android/pull/514), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Auto-hide Image Viewer toolbar (#[507](https://github.com/pachli/pachli-android/pull/507), [Angelo Suzuki](https://github.com/pachli/pachli-android/commits?author=1063155+tinsukE@users.noreply.github.com))
- Include "Notifications" in the left-side navigation menu (#[504](https://github.com/pachli/pachli-android/pull/504), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

### Significant bug fixes

- Don't crash on invalid avatars (#[566](https://github.com/pachli/pachli-android/pull/566), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Surface all exceptions to the user instead of crashing (#[565](https://github.com/pachli/pachli-android/pull/565), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Improve URL / tag / mention extraction when composing (#[564](https://github.com/pachli/pachli-android/pull/564), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Improve accessibility of ComposeActivity bottomsheets and buttons (#[548](https://github.com/pachli/pachli-android/pull/548), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Catch and swallow snackbar exception (#[536](https://github.com/pachli/pachli-android/pull/536), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Keep TabDataJsonAdapter so Moshi can deserialize in release builds (#[535](https://github.com/pachli/pachli-android/pull/535), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Sort lists by title wherever they occur ([Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Update tabs when lists are renamed or deleted ([Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Show sized placeholder for hidden account media (#[516](https://github.com/pachli/pachli-android/pull/516), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Ensure logging out accounts completes (#[515](https://github.com/pachli/pachli-android/pull/515), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Ensure files are fully downloaded before sharing (#[482](https://github.com/pachli/pachli-android/pull/482), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

### Translations

- Update Finnish translations ([Kalle Kniivilä](https://github.com/pachli/pachli-android/commits?author=kalle.kniivila@gmail.com))
- Update Kabyle translations ([ButterflyOfFire](https://github.com/pachli/pachli-android/commits?author=boffire@users.noreply.hosted.weblate.org))
- Update Spanish translations ([Juan M Sevilla](https://github.com/pachli/pachli-android/commits?author=jumase@disroot.org))

## v2.3.0

### New features and other improvements

- Display uncropped media on account media pages (#[464](https://github.com/pachli/pachli-android/pull/464), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Allow the user to trigger update checks (#[458](https://github.com/pachli/pachli-android/pull/458), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Show a preview for playable audio attachments (#[426](https://github.com/pachli/pachli-android/pull/426), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Show information about notification fetches on "About" screen (#[454](https://github.com/pachli/pachli-android/pull/454), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

### Significant bug fixes

- Keep the screen on while media is playing (#[422](https://github.com/pachli/pachli-android/pull/422), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Remove filters from Firefish capabilities (#[459](https://github.com/pachli/pachli-android/pull/459), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Improve legibility of stats labels for large font sizes (#[457](https://github.com/pachli/pachli-android/pull/457), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Use the correct font when showing inserted text (#[453](https://github.com/pachli/pachli-android/pull/453), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Show diffs for content that doesn't start with a block element (#[446](https://github.com/pachli/pachli-android/pull/446), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Show correct account/timeline when composing from notifications (#[445](https://github.com/pachli/pachli-android/pull/445), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Expedite notification fetches via UnifiedPush (#[440](https://github.com/pachli/pachli-android/pull/440), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Allow HIDE_TOP_TOOLBAR preference text to wrap (#[439](https://github.com/pachli/pachli-android/pull/439), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

### Translations

- Update Arabic translations ([ButterflyOfFire](https://github.com/pachli/pachli-android/commits?author=boffire@users.noreply.hosted.weblate.org))
- Update Dutch translations ([Martijn de Boer](https://github.com/pachli/pachli-android/commits?author=github@sexybiggetje.nl))
- Update Finnish translations ([Kalle Kniivilä](https://github.com/pachli/pachli-android/commits?author=kalle.kniivila@gmail.com))
- Update French translations ([ButterflyOfFire](https://github.com/pachli/pachli-android/commits?author=boffire@users.noreply.hosted.weblate.org))
- Update Indonesian translations ([Reza Almanda](https://github.com/pachli/pachli-android/commits?author=rezaalmanda27@gmail.com))
- Update Spanish translations ([JuanM](https://github.com/pachli/pachli-android/commits?author=jumase@disroot.org))
- Update Swedish translations ([bittin1ddc447d824349b2](https://github.com/pachli/pachli-android/commits?author=bittin@reimu.nl))

## v2.2.0

### New features and other improvements

- Disable filter functionality if not supported by the server (#[366](https://github.com/pachli/pachli-android/pull/366), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

### Significant bug fixes

- Ensure actions happen against the correct status (#[373](https://github.com/pachli/pachli-android/pull/373), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Don't leak MainActivity in DrawerImageLoader (#[382](https://github.com/pachli/pachli-android/pull/382), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Show correct trending tag values at the end of the chart lines (#[380](https://github.com/pachli/pachli-android/pull/380), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

### Translations

- Update Swedish translations ([Luna Jernberg](https://github.com/pachli/pachli-android/commits?author=bittin@reimu.nl))
- Update Indonesian translations ([Reza Almanda](https://github.com/pachli/pachli-android/commits?author=rezaalmanda27@gmail.com))
- Update Dutch translations ([Martijn de Boer](https://github.com/pachli/pachli-android/commits?author=github@sexybiggetje.nl))
- Update Portuguese (Brazil) translations ([Jener Gomes](https://github.com/pachli/pachli-android/commits?author=jenerg1@gmail.com))
- Update Finnish translations ([Kalle Kniivilä](https://github.com/pachli/pachli-android/commits?author=kalle.kniivila@gmail.com))
- Update Arabic translations ([ButterflyOfFire](https://github.com/pachli/pachli-android/commits?author=boffire@users.noreply.hosted.weblate.org))

## v2.1.1

### Significant bug fixes

- Prevent crash if a trending tab is present (#[330](https://github.com/pachli/pachli-android/pull/330), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

## v2.1.0

### New features and other improvements

- Show roles on profiles (#[312](https://github.com/pachli/pachli-android/pull/312), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Display compose button when viewing lists (#[279](https://github.com/pachli/pachli-android/pull/279), [sanao](https://github.com/pachli/pachli-android/commits?author=naosak1006@gmail.com))
- Display compose button when viewing bookmarks list (#[278](https://github.com/pachli/pachli-android/pull/278), [sanao](https://github.com/pachli/pachli-android/commits?author=naosak1006@gmail.com))

### Significant bug fixes

- Show translated content when viewing a thread (#[320](https://github.com/pachli/pachli-android/pull/320), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Calculate length of posts and polls with emojis correctly (#[315](https://github.com/pachli/pachli-android/pull/315), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Prevent crash if a preview card does not have an author ([Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Prevent crash if an error is shown in a timeline without a FAB ([Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Prevent crash showing profile if account has null `createdAt` field ([Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Prevent memory leak in CompositeWithOpaqueBackground (#[309](https://github.com/pachli/pachli-android/pull/309), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))
- Prevent UnsupportedOperationException in PachliTileService (#[288](https://github.com/pachli/pachli-android/pull/288), [Nik Clayton](https://github.com/pachli/pachli-android/commits?author=nik@ngo.org.uk))

### Translations

- Update English (United Kingdom) translations ([Jener Gomes](https://github.com/pachli/pachli-android/commits?author=jenerg1@gmail.com))
- Update Finnish translations ([Kalle Kniivilä](https://github.com/pachli/pachli-android/commits?author=kalle.kniivila@gmail.com))
- Update Japanese translations ([sanao](https://github.com/pachli/pachli-android/commits?author=jinsei@sanao.dev))
- Update Portuguese (Brazil) translations ([Jener Gomes](https://github.com/pachli/pachli-android/commits?author=jenerg1@gmail.com))
- Update Spanish translations ([Jener Gomes](https://github.com/pachli/pachli-android/commits?author=jenerg1@gmail.com))
- Update Swedish translations ([Luna Jernberg](https://github.com/pachli/pachli-android/commits?author=bittin@reimu.nl))

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
