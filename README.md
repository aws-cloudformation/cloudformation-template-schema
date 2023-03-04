# aws-cloudformation-template-schema

The CloudFormation template schema is intended to improve the authoring experience for our customers. It is a simple code process which converts our existing Resource Specifications files into a JSONSchema formatted document. This schema can be integrated into many publicly available IDEs such as Visual Studio Code &amp; PyCharm to provide inline syntax checking and code completion.

## How to use this repository

```sh
poetry install
poetry run cfn-template-schema generate --output-folder specs --type language-server
```

Will create download and modify the specs for use with a language server. It will output all the necessary files in a folder names `specs`.

## Development

`data/` folder contains all the schema files that we are using. Some of those files are templated using [Jinja](https://jinja.palletsprojects.com/)
