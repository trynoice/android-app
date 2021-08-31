#!/usr/bin/env bash

# In summary, this script does the following.
# 1. Generate full changelog using Git the tag history.
# 2. Generates a list of contributors in YAML format. The output is a top level
#    list. Each entry in this list looks like: "- ['author', 'commits url']"
# 3. Commit changes to generated files (if any).

# positional args
# 1. github repo slug (username/repo)
# 2. output path
function changelog() {
  local tag_url_fmt="https://github.com/$1/releases/tag/%(tag)"
  local tag_body_fmt="## [v%(tag)]($tag_url_fmt)%0a%0a**%(creatordate:format:%A, %-d %B %Y)**%0a%0a%(contents:body)"

  {
    printf "# Changelog\n\n"
    printf "All notable changes to this project will be documented in this file.\n\n"
    git tag --sort="-creatordate" --format="$tag_body_fmt" --list
  } > "$2"

  sed -i -E "s@(#)([0-9]+)@[\1\2](https://github.com/$1/issues/\2)@g" "$2"
}

# positional args
# 1. github repo slug (username/repo)
# 2. output path
function contributors_yaml() {
  {
    git log --pretty="- %an <%ae>%n- %cn <%ce>"
    git log --pretty="%b" | grep "Co-authored-by:"
  } > "$2"

  local commits_url_fmt="https://github.com/$1/commits?author=\2"
  sed -i -e 's/Co-authored-by:/-/g' -e 's/\r$//' "$2"
  sort -u -o "$2" "$2"
  sed -i -e '/noreply@github.com/d' -e '/hosted@weblate.org/d' -e '/\[bot\]/d' "$2"
  sed -i -E "s@- (.*) <(.*)>@- ['\1', '$commits_url_fmt']@g" "$2"
}

# positional args
# @. paths to look for new changes
function commit_changes() {
  git config --local user.email "noreply@github.com"
  git config --local user.name "GitHub"

  local author="github-actions[bot] <41898282+github-actions[bot]@users.noreply.github.com>"
  if [ -n "$(git diff "$@")" ]; then
    git add "$@"
    git commit -m "chore(project): update docs" --author "$author"
  fi
}

if [ -z "$GITHUB_REPOSITORY" ] || [ -z "$CHANGELOG_PATH" ] || [ -z "$CONTRIBUTORS_YAML_PATH" ]; then
  printf "missing at least one of the following required env variables!\n\n"
  printf "GITHUB_REPOSITORY\tgithub repo slug (username/repo)\n"
  printf "CHANGELOG_PATH\t\tfile path for the generate changelog\n"
  printf "CONTRIBUTORS_YAML_PATH\tfile path for the generate contributors list\n"
  exit 1
fi

trap exit INT
changelog "$GITHUB_REPOSITORY" "$CHANGELOG_PATH"
contributors_yaml "$GITHUB_REPOSITORY" "$CONTRIBUTORS_YAML_PATH"
commit_changes "$CHANGELOG_PATH" "$CONTRIBUTORS_YAML_PATH"
