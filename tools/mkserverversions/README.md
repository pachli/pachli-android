# mkserverversions

## Synopsis

`mkserverversions` creates a JSON5 file that maps different Fediverse server
names to a list of the different version strings seen for those servers, as
recorded by [Fediverse Observer](https://fediverse.observer).

This is used as input data for `core/network/ServerTest`, to ensure that the
version parsing code can handle real-world version strings.

Run `mkserverversions` every month to update the test data, and update the
parsing code if any of the tests fail.

## Usage

From the parent directory, run:

```shell
./runtools mkserverversions
```

Verify the modifications made to `server-versions.json5`, re-run the tests,
and commit the result.
