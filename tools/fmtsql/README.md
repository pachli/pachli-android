# fmtsql

## Synopsis

`fmtsql` formats the SQL in `@Query` annotations using `sqlfluff`, which must be installed on your system.

## Usage

From the parent directory, run:

```shell
./runtools fmtsql
```

The result may not be 100% ktlint compatible, so then run:

```shell
./gradlew ktlintformat
```

Verify the modifications made to the DAO files, and commit the result.

## Options

- `--dir`: Path to the DAO files containing `@Query` annotations. The default is the path to Pachli DAO files
- `--sqlfluff`: Full path to the `sqlfluff` executable

## Configuration

SQL formatting is controlled by the `.sqlfluff` configuration file in the project's root directory.
