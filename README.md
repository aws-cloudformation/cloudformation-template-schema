## AWS Cloudformation Template Schema

The CloudFormation template schema is intended to improve the authoring experience for our customers. 
It is a simple code process which converts our existing Resource Specifications files into a 
JSONSchema formatted document. This schema can be integrated into many publicly available IDEs 
such as Visual Studio Code & PyCharm to provide inline syntax checking and code completion.

## Key Features 

1. complete type safe template authoring with intellisence based completion.
1. Support for both YAML and JSON templates.
1. Errors flagged for missing required properties 
1. integrated deep links to CFN documentation pertinent to the resource you are editing

## How does an integration look like?

Here is a VSCode setup integration example
![VSCode](docs/images/VSCode.gif)

## How do i set it up?

For VSCode please follow the [setup/guidelines](docs/vscode/instructions.md)

## How to run the tool?

See [instructions](docs/tool/instructions.md) on running the tool locally to generate specifications for only subset of resources 

## License

This library is licensed under the Apache 2.0 License. 
