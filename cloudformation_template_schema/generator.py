import argparse
import os
from functools import wraps
from jinja2 import Environment, PackageLoader, select_autoescape
from urllib.request import urlretrieve
import zipfile
import tempfile
import json


def ignore_abort(function):
    @wraps(function)
    def wrapper(args):
        try:
            function(args)
        except (KeyboardInterrupt):
            print("\naborted")
            # pylint: disable=W0707
            raise SystemExit(1)

    return wrapper


def generate(args):
    generator = Generator(args.output_folder)
    generator.generate()


def generate_setup_subparser(subparsers: argparse.ArgumentParser, parents):
    # see docstring of this file
    parser = subparsers.add_parser(
        "generate", description=__doc__, parents=parents)
    parser.set_defaults(command=ignore_abort(generate))

    parser.add_argument(
        "--output-folder",
        help="Output the schemas to the folder",
    )


class Generator:
    schemas = [
        "conditions",
        "intrinsics",
        "mappings",
        "parameters",
        "resource.attributes",
        "base",
        "types",
    ]

    template_resource_registry = "resources.registry"
    template_resource = "resources"

    def __init__(self, output_folder: str) -> None:
        self.output_folder = output_folder
        self.resource_folder = os.path.join(output_folder, "resources")

        if not os.path.exists(self.output_folder):
            os.makedirs(self.output_folder)

        if not os.path.exists(self.resource_folder):
            os.makedirs(self.resource_folder)

    def generate(self) -> None:
        self._static()
        self._templates()

    def _static(self) -> None:
        for schema in self.schemas:
            filename = f"{schema}.schema.json"

            with open(os.path.join("cloudformation_template_schema", "data", "static", filename)) as schema_fh:
                data = json.load(schema_fh)
                with open(os.path.join(self.output_folder, filename), "w") as schema_out_fh:
                    json.dump(data, schema_out_fh, indent=2)

    def _build_args(self, type_name: str) -> dict:

        return {
            "friendlyName": type_name.replace("::", "_"),
            "fileName": type_name.replace("::", "-").lower(),
            "docName": type_name.replace("AWS::", "").replace("::", "-").lower(),
            "resourceName": type_name,
        }

    def _templates(self) -> None:

        env = Environment(
            loader=PackageLoader("cloudformation_template_schema.data"),
            autoescape=select_autoescape(),
        )

        fileobject, _ = urlretrieve(
            "https://schema.cloudformation.us-east-1.amazonaws.com/CloudformationSchema.zip"
        )

        one_of = [
            {
                "$ref": f"#/definitions/CustomResource"
            }
        ]
        resource_definitions = {}

        with open(os.path.join("cloudformation_template_schema", "data", "static", "resources.custom.schema.json")) as schema_fh:
            resource_definitions = json.load(schema_fh)

        with tempfile.TemporaryDirectory() as tmp_directory:
            tmp_directory = tempfile.TemporaryDirectory()

            with zipfile.ZipFile(fileobject, "r") as zip_ref:
                zip_ref.extractall(tmp_directory.name)

            for filename in os.listdir(tmp_directory.name):
                with open(
                    os.path.join(tmp_directory.name, filename)
                ) as schema_filename:
                    resource_schema = json.load(schema_filename)

                    template = env.get_template(
                        f"{self.template_resource_registry}.schema.json")

                    args = self._build_args(resource_schema["typeName"])
                    one_of.append(
                        {
                        "$ref": f"#/definitions/{args['friendlyName']}"
                    }
                    )
                    resource_definitions[args['friendlyName']] = json.loads(
                        template.render(args))

                    with open(
                        os.path.join(self.resource_folder, filename), "w"
                    ) as schema_properties_filename:
                        json.dump(resource_schema, schema_properties_filename, indent=2)

        template = env.get_template(f"resources.schema.json")

        with open(
            os.path.join(self.output_folder, f"resources.schema.json"), "w"
        ) as fh:
            output = json.loads(template.render(Resources=json.dumps(resource_definitions), OneOfs=json.dumps(one_of)))
            json.dump(output, fh, indent=2)
