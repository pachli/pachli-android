# :core:navigation

## package app.pachli.core.navigation

Intents for starting activities to break circular dependencies.

A common approach for surfacing type-safe (ish) intents to start activities is for the activity-to-be-launched to provide a method in a companion object that returns the relevant intent, possibly taking additional parameters that will be included in the intent as extras.

E.g., if A wants to start B, B provides the method that returns the intent.

This introduces a dependency between A and B.

This is worse if B also wants to start A.

For example, if A is `TimelineActivity` and B is`ViewThreadActivity`. The user might click a status in `TimelineActivity` to view the thread, starting `ViewThreadActivity`. But from the thread they might click a hashtag to view the list of statuses with that hashtag. Now `TimelineActivity` and `ViewThreadActivity` have a circular dependency.

Even if that doesn't happen the dependency means that any changes to B will trigger a rebuild of A, even if the changes to B are not relevant.

This package contains `Intent` subclasses that should be used instead. The `quadrant` plugin is used to generate constants that can be used to launch activities by name instead of by class, breaking the dependency chain.

If the activity's intent requires specific extras those are passed via the constructor, with companion object methods to extract them from the intent.

Using the intent classes from this package is enforced by a lint `IntentDetector` which will warn if any intents are created using a class literal.
