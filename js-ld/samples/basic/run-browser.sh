#!/bin/sh
SCRIPT_PATH=`readlink -f "$0"`

cd "`dirname "$SCRIPT_PATH"`"
python -m SimpleHTTPServer
