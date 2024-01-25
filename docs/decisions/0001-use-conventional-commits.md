# Use Conventional Commits

## Context and Problem Statement

* How to encourage PRs that focus on one issue?
* How to reduce the burden on the person responsible for compiling the release notes?

## Considered Options

* Adopt [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/)
* Adopt another standard
* Adopt a project-specific approach
* Do nothing

## Decision Outcome

Chosen option: "Adopt Conventional Commits", because comes out best (see below).

### Consequences

* Good, because PRs are more focused
* Good, because initial release notes are easier to produce
* Bad, because contributors have to learn about Conventional Commits during their first PR

## Pros and Cons of the Options

### Adopt Conventional Commits

To quote from https://www.conventionalcommits.org/en/v1.0.0/:

> The Conventional Commits specification is a lightweight convention on top of commit messages. It provides an easy set of rules for creating an explicit commit history; which makes it easier to write automated tools on top of. This convention dovetails with SemVer, by describing the features, fixes, and breaking changes made in commit messages.
>
> The commit message should be structured as follows:
>
> ```
> <type>[optional scope]: <description>
> [optional body]
>
> [optional footer(s)]
> ```

* Good, because straightforward to understand and describe
* Good, because easy to write tooling for
* Good, because tooling already exists
   * To automatically validate PR titles during PR review
   * To create initial changelogs or release notes
* Bad, because contributors unaware with Conventional Commits need to learn about it

### Adopt another standard

There doesn't seem to be one in the open source world.

### Adopt a project-specific approach

Define our own standard and formatting for commit messages.

* Bad, because it's unlikely we'd come up with anything better than Conventional Commits
* Bad, because we'd have to write our own documentation
* Bad, because we'd have to write our own tooling
* Bad, because we can expect at least some contributors to already be familiar with Conventional Commits, they shouldn't have to learn our thing too

### Do nothing

Keep the status quo.

* Good, because contributors do not need to do anything different
* Bad, because contributors and reviewers can have different expectations of what is reasonable to include in a PR description, causing friction at review time
* Bad, because it keeps the burden on the person responsible for maintaining release notes and changelogs, which remains high
