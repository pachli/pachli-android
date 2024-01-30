# mvstring

## Synopsis

`mvstring` moves a string resource from one module to another.

## Usage

From the parent directory, run:

```shell
./runtools mvstring dst-module id

./runtools mvstring src-module dst-module id
```

where:

- `src-module` - identifier for the source module that contains the string. If omitted then `app` is assumed.
- `dst-module` - identifier for the destination module
- `id` - resource ID of the string to move

Verify the modifications made to the Pachli resource files, and commit the result.
