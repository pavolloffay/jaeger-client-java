#!/usr/bin/env bash

set -o errexit -o nounset -o pipefail


if [[ "$TRAVIS_TAG" =~ ^release-[[:digit:]]+\.[[:digit:]]+\.[[:digit:]]+?$ ]]; then
    echo "Build started by version tag $tag. During the release process tags like this"
    echo "are created by the 'release' Maven plugin. Nothing to do here."
    version=$(echo "${TRAVIS_TAG}" | sed 's/^release-//')
    ./gradlew release release -Prelease.useAutomaticVersion=true -Prelease.releaseVersion=${version}
else
    ./gradlew publish
fi
