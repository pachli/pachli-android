# Contributing code (bug fixes and features)

> [!NOTE]
> These instructions are supposed to be complete and accurate. If anything is unclear or does not work please report it so it can be fixed.

## Synopsis

Thank you for wanting to improve Pachli for others by contributing bug fixes or new features.

After reading this document you will know:

- How to find ideas for things to work on
- How to communicate your work to the rest of the project
- How to prepare a PR

Before reading document you should:

- Be familiar with general practices for working with Git and GitHub
- Installed and configured Android Studio and built an application

as this document is not a primer on those topics.

Every code change involves at least two people; the person making the change, and the person reviewing it. These guidelines are intended to make that process go smoothly, being respectful of the time of everyone involved.

## Read the contributor license agreement

The project uses a contributor license agreement (CLA) to ensure that all code contributions are properly licensed for the future.

Please read the [contributor license agreement](https://github.com/pachli/pachli-android/CLA_IC.md) before you start. It would be terrible if you spent a lot of time working on a PR, only to discover at the end of the process that you cannot submit it because you cannot sign the CLA.

If your employment contract forbids working on open source code without explicit signoff, or requires your employer to sign the CLA then please see the [entity contributor license agreement](https://github.com/pachli/pachli-android/CLA_ENTITY.md) instead.

## Find something to work on

https://github.com/pachli/pachli-android/contribute is a list of open issues that have been tagged `good first issue`. This means the changes should be relatively small and self-contained.

You do not have to restrict yourself to just working on one of those issues, but as the name suggests, they're a good place to start.

The [discussions](https://github.com/pachli/pachli-android/discussions) may have suggested ideas too.

And of course, you may have encoutered a bug in your personal use of the app, or would find a feature to be useful.

## Use your judgement when following these guidelines

The following guidelines are not appropriate for all changes.

Fixing typos, improving code comments, small refactorings, trivial bugfixes and the like will not benefit from a heavyweight process, and if you happen to see a problem in a piece of code that's easy to fix then you can just send a PR.

Just beware that changes that we imagine to be small before starting have a nasty habit of becoming larger than expected.

Halfway through an 800 line refactor is the wrong time to think "Maybe I should have asked for feedback about this before starting..."

So use your judgement.

## Communicating to other project members

### Create an issue, if appropriate

If an issue does not already exist for whatever you want to work on, please create one.

This ensures the other contributors know what's happening, and might invite useful feedback from others.

For features it also provides an opportunity for the project stewards to report that the particular feature, no matter how well written, would not be accepted in to the app.

Depending on the size of the problem to fix or the feature to add the issue is also a place to provide any ideas you have about how to solve the problem or implement the feature, and get feedback on those before proceeding.

### Clearly state that you are working on the issue

If you have the "GitHub" triage role, and can assign the issue to yourself, do so.

If you do not then leave a comment on the issue to make it clear you are working on it.

### Update the issue as you work

As you work on the issue please update it with your progress, at least until you have a PR that can be looked at.

In particular, it is your responsibility to keep the issue up to date. It is not the project's responsibility to periodically check in with you to find out how you're doing.

If you have claimed responsibility for an issue (either by assigning it to yourself, or leaving a comment saying you are working on it) please provide an update at least every seven days.

It is OK if that update is "I have not been able to make more progress on this" or "Here are a bunch of things I've discovered don't work".

If you will be away for an extended period of time and need to pause work please also note that on the issue.

If you have to abandon work on an issue for any reason please leave a comment noting that you will not able to do any further work, and unassign the issue if appropriate.

> [!IMPORTANT]
> You do not need to provide **any** justification for why you have to pause work or are unable to complete an issue. It is enough just to let other contributors know that the issue is available for someone else to work on.

## Writing and submitting pull requests

Pull requests (PRs) are the primary unit of collaboration for code.

### Work on branches in your own fork of the repository

Do not clone the `pachli-android` repository. Instead, create a fork, create a branch in your fork from the `main` branch, and commit your changes to that branch.

See the GitHub [Collaborating with pull requests](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/getting-started/about-collaborative-development-models) documentation.

### Name branches using the issue ID

> [!NOTE]
> This is entirely optional, but I have found it helpful in my own work.

If your PR will resolve an open issue consider naming the branch starting with the issue's ID.

So if you will resolve issue #1234, name the branch `1234-...`.

### Choose a build variant

Pachli has 2 x 3 build flavours.

The two colour flavours are `blue` and `orange`. The blue flavour is used to build each production release. The `orange` flavour can be installed alongside the `blue` flavour, and is ideal if you want to keep the released version of Pachli and your testing version installed side by side.

Pachli Current is built from the `orange` flavour.

The three store flavours are `fdroid`, `github`, and `google`. These contain store-specific code; for example, checking for an updated release.

Typically you would configure the build variant in Android Studio with Build > Select Build Variant..., and select `orangeFdroidDebug`.

This is not mandatory, but may make developing easier for you.

### Code style

#### `ktlintCheck` and `ktlintFormat`

The project uses [ktlint](https://pinterest.github.io/ktlint/) to enforce common code and formatting standards.

You can check your code before creating the PR with the `ktlintCheck` task.

```shell
./gradlew ktlintCheck
```

Most code formatting issues can be automatically resolved with the `ktlintFormat` task.

```shell
./gradlew ktlintFormat
```

The code in your PR will be checked for this every time it changes. If it is not lint-clean and automated fixes are possible they will be added as comments to the PR.

#### Questions of taste

Some code style issues are questions of taste, where developers might reasonably differ but the project has a specific stance.

Please read the [Code style guide](/docs/code-style.md).

### Individual commits

A PR is typically made up multiple commits.

With very few exceptions this project squash-merges all PRs to maintain a linear commit history on the `main` branch.

Therefore **we do not care** about the "quality" of the individual commits that make up a single PR. Within the PR a single commit might change multiple things, have a non-obvious commit message, be littered with `TODO` comments, etc. Please do not think that every commit you make as you complete your PR needs to be perfect.

### Create a PR when you have something to share

You may have code to share before you consider the work complete. Typically because you want feedback before progressing.

Creating a PR in this state is absolutely fine. You can either:

- Create the PR in a draft state, using the GitHub UI
- Create a regular PR, and set the title prefix to `wip:` (for "work in progress")

In both cases you should leave a comment that @-mentions one or more people from the project and is very clear about what sort of feedback you are looking for before you can proceed.

### Preparing a PR for review

When you have code that you think is ready for a final review you can either create a PR or finalise a draft you prepared earlier.

A PR should be self-contained. The changes within the PR should strongly relate to one another, without it being reasonable to separate them in to multiple changes.

In particular, **do not mix** changes that modify functionality with changes that just move code around or reformat it.

This makes things needlessly difficult for your reviewers.

#### PR title and description

The project uses the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) standard for commit messages. If you are not familiar with them [Conventional Commits: A better way](https://medium.com/neudesic-innovation/conventional-commits-a-better-way-78d6785c2e08) is also a good introduction.

> [!NOTE]
> See [docs/decisions/0001-use-conventional-commits.md](/docs/decisions/0001-use-conventional-commits.md)

The PR's title and description will become the first line and remaining body of the commit message when the PR is merged, so your PR title and description should also follow the conventional commits approach.

##### PR title

Your PR title should be `<type>: <title>`, where `<type>` is one of the conventional commit types, and `<title>` should be a very short description of the effect of the PR's change.

The types are:

- `build`, modify the build system
- `change`, change the implementation of an existing feature
- `chore`, maintenance work
- `ci`, change the CI configuration
- `deprecate`, deprecate but do not remove existing functionality
- `docs`, change the documentation
- `feat`, implement a new feature
- `fix`, fix a bug
- `perf`, improve performance
- `refactor`, refactor existing code
- `remove`, remove a feature
- `revert`, revert a previous PR
- `security`, resolve a security issue
- `style`, changes code style
- `test`, modify the test suite
- `wip`, work-in-progress

More details on each is in [docs/decisions/0001-use-conventional-commits.md](/docs/decisions/0001-use-conventional-commits.md).

`feat` for new features and `fix` for bug fixes are the most common.

A PR should only have one type. Needing two or more (e.g., `fix` and `refactor`) indicates the PR is too large a change, and should be split in to multiple distinct PRs.

The PR title should be written in the imperative mood, so "Fix ...", or "Add ...", or "Update ..." or similar, **not** "Fixes ...", "Adds ...", etc.

A helpful way to remember this is that you should be able to add the PR title to the end of the sentence "If merged, this PR will..." and have a sensible sentence.

The PR title and decription should be able to stand alone -- someone with the appropriate technical skill should able to read the title and description to understand what was done without needing to refer to e.g., a conversation carried out in a related issue.

##### PR description

The PR description should describe **why the PR is needed**, **what it does**, and if appropriate, a **justification** for doing it this way.

This clearly communicates the necessary context for understanding the code to people looking at the PR now and in the future.

In particular, **it is critical that the PR description tells reviewers what your intent is for the code**, so they can verify that the code you've written matches what you intended.

##### PR description footer

If your PR is associated with one or more existing issues, **and** merging the PR will resolve the issues you should add `Fixes ` and a comma-separated list of `#`-prefixed issue numbers as the last line in the PR.

For example, `Fixes #1234, #5678`.

If you do this GitHub will:

- Add a link in the UI between the issue and the PR, and vice-versa
- Close the mentioned issues when the PR is merged

Only do this if the PR will definitely resolve the issues. If it only partially resolves the issues you can mention them in the PR description, but do not reference them like this in the footer.

#### Bad and good bad examples

Here's a straightforward example of a bad PR title and description.

```
Change the icon colours
```

No `type` tag, the title is vague, there's no description, and it's impossible to determine whether the PR as written achieves the author's intent.

Here's the good version of the same thing.

```
fix: Ensure icons are visible in the light theme

The previous code used `@color/white` as the icon colour when the
control is in its normal state, so the icons were invisible in the
light colour theme.

Replace with the `colorControlNormal` attribute, which is correct
across all themes and the standard Android attribute for use on
controls.

Fixes #1234
```

As well as conveying more information to me as the reviewer this allows me to perform a more thorough review.

- Does the PR update code that didn't use `@color/white`?
- Are there other instances of `@color/white` that this PR missed?
- Does the PR use the correct spelling of `colorControlNormal` everywhere?

#### Cleanly merge back in to `main`

While you were working on your PR other changes may have happened on the `main` branch.

Some of those changes may have overlapped with changes you make in your PR, and your PR can not be merged cleanly back in to `main`.

You should periodically merge changes from the `main` branch in to your PR branch, and keep your PR up to date with `main` during the review process.

If your PR can not be cleanly merged in to `main` it is difficult to review effectively, because merging the changes from `main` in to your PR will invalidate the review. You've changed the code, so the reviewer needs to look at it again.

#### Tests

The project has a number of automated tests, they will automatically be run on your PR when it is submitted.

You can run them with the `app:testOrangeFdroidDebugUnitTest` task.

```shell
./gradlew app:testOrangeFdroidDebugUnitTest
```

Where practical new code should include new unit tests.

#### Android lint

The project uses Android lint to check for many common errors or questionable practices. The Android lint checks will automatically be run on your PR when it is submitted.

You can run them with the `app:lintOrangeFdroidDebug` task.

```shell
./gradlew app:lintOrangeFdroidDebug
```

A number of pre-existing lint errors and warnings have been grandfathered in to the project, and can be seen in the `app/lint-baseline.xml` file.

These are being removed over time, please do not add to them.

If your PR fixes some of the existing lint issues, or moves code so that the baseline file is no longer valid you can you regenerate it with the `updateLintBaselineOrangeFdroidDebug` task.

```shell
./gradlew updateLintBaselineOrangeFdroidDebug
```

#### Screenshots

If your PR makes UI changes please include screenshots as a PR comment so the reviewer can easily see what's changed. Typically as one or more pairs of before-and-after screenshots.

First, create your PR as normal.

Then take one or more screenshots. To create a screenshot run the code with your PR in an emulator, and from the Android Studio "logcat" window click the "Take Screenshot" button.

A window should appear containing the screenshot. You do not need to save this file, choose "Copy to Clipboard".

On the GitHub page for your PR create a new comment, and paste (Ctrl-V / Cmd-V) the screenshot you copied to your clipboard. GitHub will automatically upload the clipboard contents as a file and paste a reference to the file in to the PR comment.

### Pre-review PR checklist

This checklist may be helpful when you are creating your first few PRs. The PR:

- [ ] ... is made from my fork of the Pachli repository
- [ ] ... is branched from `main`
- [ ] ... title follows Conventional Commits:
    - [ ] starting with the correct type (`fix:`, `feat:`, etc)
    - [ ] and makes sense prefixed with "If merged, this PR will ..."
- [ ] ... description is complete, explaining:
    - [ ] Why the PR is needed
    - [ ] What it does
    - [ ] If appropriate, a justification for the approach
    - [ ] And has a "Fixes ..." footer if it fixes an issue
- [ ] ... merges cleanly in to the `main` branch
- [ ] ... passes `ktlintCheck`
- [ ] ... passes all tests
    - [ ] ... and adds new ones if appropriate
- [ ] ... passes Android lint
- [ ] ... followup comments contain before/after screenshots, if appropriate

## Sign the CLA

If this is your first time submitting a PR to the project you will need to sign the CLA.

This is automated; shortly after submitting your PR for review a comment will automatically be attached with a link to a site where you can read the CLA (again) and sign the CLA electronically using your GitHub account.

You will only need to do this once.

## Code review

A great deal can be written about how to effectively perform code reviews, and a great deal already has.

For the moment PR authors and code reviewers should both read:

- https://google.github.io/eng-practices/review/reviewer/
- https://stackoverflow.blog/2019/09/30/how-to-make-good-code-reviews-better/

as they neatly set out best practices the project tries to adhere to.

A code review is first and foremost a dialogue between two people who are both working to improve the software for our users.


---

## Project norms for GitHub issue management

> [!NOTE]
> Some contributors have additional GitHub access rights to manage issues and issue metadata. This section is for them.

### How issues are assigned

1. An issue is never assigned *by* someone *to* someone else. As a volunteer project the assigner can never be sure that the assignee is available to resolve the issue right now.

2. Therefore, **issues are only ever self-assigned**.

3. Self-assigning an issue is a clear statement of “I will do the work necessary to resolve this issue. If I can not do that in reasonable time I will un-assign the issue”

4. If you have assigned an issue to yourself you are expected to provide periodic updates to that issue, at least one update every 7 days. This is to ensure that the state of the work on the issue is clear to everyone else on the project.

    a. The update may be a variation of “I have not been able to work on this”. That's OK. The important thing is that this is valuable information to convey to everyone else.

    b. If a PR is associated with the issue then updates to the PR are treated as updates to the issue

5. It is OK if you have assigned an issue to yourself, and then discover that you are not able to complete it or provide useful updates. If that happens just un-assign the issue. You don't have to provide a justification for why you can't do it at this time. It's enough to let the rest of the team know that someone else will need to pick this up.

6. If you are working on an issue and someone else needs to make a decision, `@` them into the issue, and add the `blocked` label so they can find it.

    a. Per item (1) do not assign the issue to the person who needs to make the decision.

    b. Being blocked waiting on a decision does not remove the requirement to provide an update every 7 days. If you are blocked the update is “Blocked, waiting on a decision from @whoever” or similar. Again, this is all about clearly communicating the state of the work to the rest of the team.

### Setting issue labels

The labels communicate three different things about the issue:

- The issue's type: `bug`, `enhancement`, `question`, `refactoring`
- The issue's state: `blocked`, `duplicate`, `help wanted`, `invalid`, `wontfix`
- Additional metadata: `good first issue`

From that it follows:

- Every issue should have exactly one of the type labels
    - The type may change over time. For example, the discussion on an issue initially labelled `question` may uncover a `bug` or `enhancement` proposal.
- The state labels are optional
    - `blocked` and `help wanted` only makes sense on issues that are open
    - `duplicate`, `invalid`, `wontfix` only make sense on issues that are closed

The line between a "bug" and an "enhancement" can be fuzzy. For example, at the time of writing users can upload profile photos, but they cannot delete them. Is that a bug to be fixed, or an enhancement to the existing functionality?

We don’t think it matters yet. We’re not tracking metrics around "How many bugs did we fix?" or "How many new features did we provide?", so don’t worry over which label is more appropriate.

### Finding stale issues

GitHub does not have native functionality to "Show all issues assigned to me that have not been updated in the last 7 days", but that can be performed by a browser bookmarklet, with the following Javascript.

```javascript
javascript: (
  () => {
    const d = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
    const yyyy = d.getFullYear();
    const mm = d.getMonth() + 1;
    const dd = d.getDate();
    const yyyymmdd = `${yyyy}-${mm.toString().padStart(2, '0')}-${dd.toString().padStart(2, '0')}`;
    const q = `is:open updated:<=${yyyymmdd} assignee:@me`;
    window.open("https://github.com/pachli/pachli-android/issues?q=" + encodeURIComponent(q), '_self');
  }
)();
```
