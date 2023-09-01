# mklanguages

## Synopsis

`mklanguages` ensures that the language list in Pachli is:

- Up to date
- Sorted according to ICU guidelines
- Uses language names according to ICU guidelines

Use `mklanguages` whenever a new language is added to Pachli.

## Usage

From the parent directory, run:

```shell
./runtools mklanguages
```

Verify the modifications made to the Pachli resource files, and commit the result.
