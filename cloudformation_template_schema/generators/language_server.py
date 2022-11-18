from typing import Dict, List, Union
from .base import BaseGenerator
from ..schema import Resource
import json


class RequiredProperty:
    def __init__(self, required: bool, strict: bool) -> None:
        if required:
            self._required = "Yes"
        else:
            self._required = "No"
        self._strict = strict

    def update(self, value: bool) -> None:
        if self._strict:
            return

        if self._required == "Yes" and not value:
            self._required = "Conditional"
        elif self._required == "No" and value:
            self._required = "Conditional"

    def get(self) -> str:
        return self._required


class RequiredManager:
    def __init__(self, schema: Dict) -> None:
        self._properties = {}
        for item in schema.get("properties").keys():
            self._properties[item] = None

        self._walk(schema, True)

    def _walk(self, schema: Dict, strict: bool) -> None:
        for allOf in schema.get("allOf", []):
            self._walk(allOf, strict if strict else False)
        for oneOf in schema.get("oneOf", []):
            self._walk(oneOf, False)
        for anyOf in schema.get("anyOf", []):
            self._walk(anyOf, False)
        required = schema.get("required", [])
        for property in self._properties.keys():
            if property in required:
                if self._properties[property] is None:
                    self._properties[property] = RequiredProperty(True, strict)
                else:
                    self._properties[property].update(True)
            else:
                if self._properties[property] is None:
                    self._properties[property] = RequiredProperty(False, False)
                else:
                    self._properties[property].update(False)

    def get(self, key: str) -> str:
        if self._properties[key] is None:
            return "No"
        return self._properties[key].get()


class LanguageServerGenerator(BaseGenerator):
    def __init__(
        self,
        output_folder: str,
        template_static: str = "cloudformation_template_schema.data.static",
        template_package: str = "cloudformation_template_schema.data.language_server",
    ) -> None:
        super().__init__(output_folder, template_static, template_package)

    def _convert_definition(self, type: Dict) -> None:
        pass

    def _build_args(self, resource: Resource) -> dict:
        doc_name = resource.type_name.replace("::", "-").replace("AWS-", "").lower()
        doc_url = f"https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-resource-{doc_name}.html"

        required = ["Type"]
        if resource.schema.get("required"):
            required.append("Properties")
        return {
            "friendlyName": resource.type_name.replace("::", "_"),
            "fileName": resource.type_name.replace("::", "-").lower(),
            "resourceName": resource.type_name,
            "markdownDescription": json.dumps(
                f"{resource.schema.get('description', '')}  \n[Docs]({doc_url})"
            ),
            "required": json.dumps(required),
        }

    def _build_description(self, schema: Dict):
        property_template = self.env.get_template(f"property.md")

        return property_template.render(prop=schema)

    def _walk(self, schema: Dict, path: List, resource: Resource) -> None:
        if not isinstance(schema, dict):
            return schema

        if isinstance(schema.get("properties"), dict):
            required = RequiredManager(schema)
            for k, v in schema.get("properties", {}).items():
                resource.setval(
                    path[:] + ["properties", k, "___IsRequired"], required.get(k)
                )

        for k in list(schema):
            v = schema[k]
            if isinstance(v, dict):
                self._walk(v, path[:] + [k], resource)

        if ("description" in schema or "type" in schema) and len(path) > 1:
            resource.setval(
                path[:] + ["markdownDescription"], self._build_description(schema)
            )

        # cleanup the additional properties we needed
        for k in list(schema):
            # pattern doesn't always work in typescript. For sanity we are removing it
            if k in ["___IsRequired", "___Conditional", "___CreateOnly", "pattern"]:
                resource.delval(path + [k])

    def _resource(self, resource: Resource) -> None:

        for property_path in resource.schema.get("conditionalCreateOnlyProperties", []):
            resource.setval(property_path.split("/")[1:] + ["___Conditional"], True)

        for property_path in resource.schema.get("createOnlyProperties", []):
            resource.setval(property_path.split("/")[1:] + ["___CreateOnly"], True)

        self._walk(resource.schema, [], resource)

        return super()._resource(resource=resource)
