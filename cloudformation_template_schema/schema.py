from urllib.request import urlretrieve
import tempfile
import zipfile
import os
import json
from typing import Generator, Dict, List
import logging


class Resource:
    filename: str
    type_name: str
    schema: Dict

    def __init__(self, filename: str, type_name: str, schema: Dict) -> None:
        self.filename = filename
        self.type_name = type_name
        self.schema = schema

    def getval(self, keys: List):
        data = self.schema
        for k in keys:
            data = data[k]
        return data

    def setval(self, keys: List, val) -> None:
        data = self.schema
        lastkey = keys[-1]
        for k in keys[:-1]:  # when assigning drill down to *second* last key
            try:
                data = data[k]
            except KeyError as e:
                if isinstance(data, dict):
                    if "$ref" in data:
                        self.setval(data["$ref"].split("/")[1:] + [lastkey], val)
                    else:
                        logging.warning(
                            "KeyError: {key} in type {type_name}".format(
                                key=k, type_name=self.type_name
                            )
                        )
                        return
                else:
                    logging.warning(
                        "KeyError: {key} in type {type_name}".format(
                            key=k, type_name=self.type_name
                        )
                    )
                    return
        data[lastkey] = val

    def delval(self, keys: List):
        data = self.schema
        lastkey = keys[-1]
        for k in keys[:-1]:  # when assigning drill down to *second* last key
            data = data[k]
        del data[lastkey]


class Schema:

    schema_urls = {
        "us-east-1": "https://schema.cloudformation.us-east-1.amazonaws.com/CloudformationSchema.zip",
    }

    def __init__(self) -> None:
        self.resources = []

    def _load_region(self, region: str) -> Generator[Resource, None, None]:

        fileobject, _ = urlretrieve(self.schema_urls[region])

        with tempfile.TemporaryDirectory() as tmp_directory:
            tmp_directory = tempfile.TemporaryDirectory()

            with zipfile.ZipFile(fileobject, "r") as zip_ref:
                zip_ref.extractall(tmp_directory.name)

                for filename in os.listdir(tmp_directory.name):
                    with open(
                        os.path.join(tmp_directory.name, filename)
                    ) as schema_filename:
                        resource_schema = json.load(schema_filename)

                        type_name = resource_schema.get("typeName")
                        if not type_name or type_name in self.resources:
                            continue

                        self.resources.append(type_name)
                        yield Resource(filename, type_name, resource_schema)

    def get_resources(self) -> Generator[Resource, None, None]:
        # There isn't one complete schema. We must process each region

        for region in self.schema_urls.keys():
            for resource in self._load_region(region=region):
                yield resource
