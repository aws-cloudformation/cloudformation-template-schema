PyCharm instructions 

**Install PyCharm**

1. Install PyCharm [code binary](https://www.jetbrains.com/pycharm/download/).
2. Start PyCharm, on macOS press CMD+<SPACE> and type __pycharm__

**JSON/YAML Setup**

1. Open __Preferences__
1. Navigate to __Languages and Frameworks__ > __Schemas and DTDs__ > __JSON Schema Mappings__
1. Add new Mapping (__Ctrl+N__)
1. Name the Mapping (e.g; "CFN Template Schema")
1. Enter a Schema URL (e.g; `https://s3.amazonaws.com/cfn-resource-specifications-us-east-1-prod/schemas/2.15.0/all-spec.json`)
1. Add a mapping to a folder or file path pattern (e.g; `"*-template.json`)
1. Select the appropriate Schema Version: __JSON schema version 7__
1. Click __OK__ to save.
1. Create new file with the extension specified in the mapping (e.g; `my-app-template.json`) 

**Gotchas**

1. PyCharm does not provide the `description` context on mouse-hover (which other IDEs do)
