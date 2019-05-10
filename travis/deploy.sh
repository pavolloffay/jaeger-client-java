#!/usr/bin/env bash

set -o errexit -o nounset -o pipefail


setup_git() {
  git config --global user.email "travis@travis-ci.org"
  git config --global user.name "Travis CI"
}

if [[ "$TRAVIS_TAG" =~ ^release-[[:digit:]]+\.[[:digit:]]+\.[[:digit:]]+?$ ]]; then
    echo "We are on release- tag"
    echo "bumping versions and creating vx.x.x tag"
    echo "final artifact will be published in build for the tag"
    setup_git
    version=$(echo "${TRAVIS_TAG}" | sed 's/^release-//')
    ./gradlew release release -Prelease.useAutomaticVersion=true -Prelease.releaseVersion=${version}
else
    ./gradlew publish
fi
