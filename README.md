## AWS CloudFormation Template Schema

The CloudFormation template schema is intended to improve the authoring experience for our customers. 
It is a simple code process which converts our existing Resource Specifications files into a 
[JSON Schema](https://json-schema.org) formatted document. This schema can be integrated into many publicly available IDEs 
such as Visual Studio Code & PyCharm to provide inline syntax checking and code completion.

## Key Features 

1. Complete type-safe template authoring with IntelliSense-based completion
1. Support for both YAML and JSON templates
1. Errors flagged for missing required properties
1. Integrated deep links to CloudFormation documentation for the resource or template section you are editing

## What does an integration look like?

Here is a VSCode setup integration example
![VSCode](docs/images/VSCode.gif)

## How do I set it up?

#### VS Code

For [VS Code](https://code.visualstudio.com/) please follow the [setup/guidelines](docs/vscode/instructions.md)

#### PyCharm

For [PyCharm](https://www.jetbrains.com/pycharm/) please follow the [setup/guidelines](docs/pycharm/instructions.md)


## How do I build and run the tool?

See [instructions](docs/tool/instructions.md) which describes how to run the tool locally, to generate specifications for only subset of resources or AWS regions.


## License

This library is licensed under the Apache 2.0 License. 
