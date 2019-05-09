#!/usr/bin/env bash

set -o errexit -o nounset -o pipefail


./semver.sh

${TRAVIS_TAG}


git commit -m "" -s
