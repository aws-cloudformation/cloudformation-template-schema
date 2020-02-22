PyCharm instructions
------

**Install PyCharm**

1. Install PyCharm [code binary](https://www.jetbrains.com/pycharm/download/).
2. Start PyCharm, on macOS press CMD+<SPACE> and type __pycharm__

**JSON/YAML Setup**

1. Open __Preferences__
2. Navigate to __Languages and Frameworks__ > __Schemas and DTDs__ > __JSON Schema Mappings__
3. Add new Mapping (__Ctrl+N__)
4. Name the Mapping (e.g; "CFN Template Schema")
5. Enter a Schema URL (e.g; `https://s3.amazonaws.com/cfn-resource-specifications-us-east-1-prod/schemas/2.15.0/all-spec.json`)
6. Add a mapping to a folder or file path pattern (e.g; `"*-template.json`)
7. Select the appropriate Schema Version: __JSON schema version 7__
8. Click __OK__ to save.
9. Create new file with the extension specified in the mapping (e.g; `my-app-template.json`) 

Gotchas
------

1. PyCharm does not provide the `description` context on mouse-hover (which other IDEs do)

Troubleshooting 
------
**JSON Schema not found or contain error in 'all-spec.json': Can not load code model for JSON Schema file 'all-spec.json'**

Pycharm use the `idea.max.intellisense.filesize` platform property to sets the maximum size of files for which PyCharm provides code assistance and to load a JSON schema.

By default this property is set to `2500` kilobytes.

In order to load a schema bigger than this size, you have to edit this property to a number greater than the size of your schema, for example `5000` kilobytes. In order to do that go to `Help | Edit Custome Properties` and add the following line: `idea.max.intellisense.filesize=5000`.

Restart Pycharm, you can now load the file.

This restriction only applies to local file, if for whatever reason you can't edit this file you can setup a local webserver (For example with `python -m SimpleHTTPServer`) and serve your schema file through http.




