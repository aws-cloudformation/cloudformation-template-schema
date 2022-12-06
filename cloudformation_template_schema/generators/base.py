import os
from typing import Dict
from jinja2 import Environment, PackageLoader, select_autoescape
from ..schema import Schema, Resource
import zipfile
import tempfile
import json
import importlib.resources as pkg_resources


class BaseGenerator:
    static_schemas = [
        "conditions",
        "intrinsics",
        "mappings",
        "parameters",
        "resource.attributes",
        "base",
        "types",
    ]

    template_resource_registry = "resources.registry"

    def __init__(
        self,
        output_folder: str,
        template_static: str = "cloudformation_template_schema.data.static",
        template_package: str = "cloudformation_template_schema.data.base",
    ) -> None:
        self.output_folder = output_folder
        self.resource_folder = os.path.join(output_folder, "resources")
        self.template_static = template_static
        self.template_package = template_package
        if not os.path.exists(self.output_folder):
            os.makedirs(self.output_folder)

        if not os.path.exists(self.resource_folder):
            os.makedirs(self.resource_folder)

        self.env = Environment(
            loader=PackageLoader(self.template_package),
            autoescape=select_autoescape(),
        )

        self.schema = Schema()

    def generate(self) -> None:
        self._static()
        self._resources()

    def _static(self) -> None:
        for schema in self.static_schemas:
            filename = f"{schema}.schema.json"

            data = json.loads(pkg_resources.read_text(self.template_static, filename))
            self._write_json(os.path.join(self.output_folder, filename), data)

    def _build_args(self, resource: Resource) -> dict:
        return {
            "friendlyName": resource.type_name.replace("::", "_"),
            "fileName": resource.type_name.replace("::", "-").lower(),
            "docName": resource.type_name.replace("AWS::", "")
            .replace("::", "-")
            .lower(),
            "resourceName": resource.type_name,
        }

    def _write_json(self, filename: str, data: Dict) -> None:
        if os.path.exists(filename) == True:
            return
        with open(filename, "w") as fh:
            json.dump(data, fh, indent=2)

    def _resource(self, resource: Resource) -> None:
        # Processes each schema updating it as needed

        # read only attributes cannot be set so we don't want them
        # for things like autocomplete or validation
        read_only_properties = resource.schema.get("readOnlyProperties", [])

        attributes = {}

        for section in ["properties", "definitions"]:
            for name in list(resource.schema.get(section, {})):
                if f"/{section}/{name}" in read_only_properties:
                    attributes[name] = resource.schema[section][name]
                    del resource.schema[section][name]

        resource.schema["attributes"] = attributes
        self._write_json(
            os.path.join(self.resource_folder, resource.filename),
            resource.schema,
        )

    def regions(self) -> None:
        pass

    def _resources(self) -> None:

        one_of = [{"$ref": f"#/definitions/CustomResource"}]
        resource_definitions = {}

        resource_definitions = json.loads(
            pkg_resources.read_text(
                self.template_static, "resources.custom.schema.json"
            )
        )

        for resource in self.schema.get_resources():
            template = self.env.get_template(
                f"{self.template_resource_registry}.schema.json"
            )

            args = self._build_args(resource)
            one_of.append({"$ref": f"#/definitions/{args['friendlyName']}"})
            resource_definitions[args["friendlyName"]] = json.loads(
                template.render(args)
            )

            self._resource(resource=resource)

        template = self.env.get_template(f"resources.schema.json")

        output = json.loads(
            template.render(
                Resources=json.dumps(resource_definitions), OneOfs=json.dumps(one_of)
            )
        )
        self._write_json(
            os.path.join(self.output_folder, f"resources.schema.json"), output
        )
