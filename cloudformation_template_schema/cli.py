"""This tool provides support for creating generating JSON Schemas for CloudFormation
"""
import argparse
import logging
import time
from importlib import metadata
from .generate import generate_setup_subparser

EXIT_UNHANDLED_EXCEPTION = 127


class UTCFormatter(logging.Formatter):
    converter = time.gmtime


def main(args_in=None):  # pylint: disable=too-many-statements
    """The entry point for the CLI."""
    log = None
    # see docstring of this file
    parser = argparse.ArgumentParser(description=__doc__)
    # the default command just prints the help message
    # subparsers should set their own default commands
    # also need to set verbose here because now it only gets set if a
    # subcommand is run (which is okay, the help doesn't need it)

    def no_command(args):
        if args.version:
            print("cfn", metadata.version("cloudformation_template_schema"))
        else:
            parser.print_help()

    parser.set_defaults(command=no_command, verbose=0)
    parser.add_argument(
        "--version",
        action="store_true",
        help="Show the executable version and exit.",
    )
    base_subparser = argparse.ArgumentParser(add_help=False)

    parents = [base_subparser]
    subparsers = parser.add_subparsers(dest="subparser_name")
    generate_setup_subparser(subparsers, parents)

    args = parser.parse_args(args=args_in)

    log = logging.getLogger(__name__)
    log.debug("Logging set up successfully")
    log.debug("Running with: %s", args)

    args.command(args)
    log.debug("Finished %s", args)
