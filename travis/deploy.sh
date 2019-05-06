#!/usr/bin/env bash

set -o errexit -o nounset -o pipefail


if [[ "$TRAVIS_TAG" =~ ^release-[[:digit:]]+\.[[:digit:]]+\.[[:digit:]]+?$ ]]; then
    echo "We are on release- tag"
    echo "bumping versions and creating vx.x.x tag"
    echo "final artifact will be published in build for the tag"
    version=$(echo "${TRAVIS_TAG}" | sed 's/^release-//')
    ./gradlew release release -Prelease.useAutomaticVersion=true -Prelease.releaseVersion=${version}
else
    ./gradlew publish
fi
