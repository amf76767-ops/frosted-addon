#!/usr/bin/env fish

# Publishes build/libs/frosted-<version>.jar as a GitHub release.
# Usage: fish publish.fish "Release notes text"
# Needs the GitHub CLI (sudo pacman -S github-cli) and `gh auth login` once.

set version (string match -r '^mod_version=(.+)$' < gradle.properties)[2]
set jar build/libs/frosted-$version.jar

if not test -f $jar
    echo "Build first: ./gradlew build   (missing $jar)"
    exit 1
end

set notes "Frosted $version"
if test (count $argv) -gt 0
    set notes $argv[1]
end

gh release create v$version $jar --title "Frosted $version" --notes "$notes"
or exit 1

echo "Published v$version. The updater in older installs will offer it."
