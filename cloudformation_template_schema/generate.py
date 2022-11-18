import argparse
from functools import wraps
from .generators import BaseGenerator, LanguageServerGenerator


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
    if args.type not in [None, "language-server"]:
        raise ValueError("Use no type or language-server")

    generator = BaseGenerator(args.output_folder)
    if args.type == "language-server":
        generator = LanguageServerGenerator(args.output_folder)

    generator.generate()


def generate_setup_subparser(subparsers: argparse.ArgumentParser, parents):
    # see docstring of this file
    parser = subparsers.add_parser("generate", description=__doc__, parents=parents)
    parser.set_defaults(command=ignore_abort(generate))

    parser.add_argument(
        "--output-folder",
        help="Output the schemas to the folder",
    )

    parser.add_argument(
        "--type",
        help="Specify the type of the schema",
    )
