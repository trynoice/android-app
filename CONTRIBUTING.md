# Contributing

We welcome contributions of all kinds and sizes. This includes everything from simple bug reports to large features.

## Workflow

- We love GitHub issues!
- For small feature requests, an issue first proposing it for discussion or demo
  implementation in a PR suffice
- For big feature requests, please open an issue to avoid wasting time on a feature
  that might need reworking
- Small pull requests for things like typos, bug fixes, etc are always welcome

## Getting started

1. Ensure that you have the latest Android SDK tools installed on your machine
2. Fork the repository to create a copy in your GitHub account. The forked repository
   should be accessible at `https://github.com/<your-username>/noice`
3. Clone the forked repository to your machine
4. Open the existing project using Android Studio or any editor of your choice

## Adding sounds

Feel free to add more sounds to Noice taking the following under consideration.

- Looping sounds should be at least 30 seconds and at most 2 minutes long. These are not
  hard limits but the goal should be to minimize the ease of recognizing recurring
  patterns in loops
- All sounds should be encoded to `mp3` format with the following configuration. I use
  [Audacity](https://www.audacityteam.org) for editing audio
  - Sample rate: 44.1 kHz
  - Quality: Standard, 170-210 kbps
  - Variable Speed: Fast
  - Channel Mode: Stereo

## Best Practices

- Let Android Studio do the code formatting
- Include tests when adding new features
- When fixing bugs, start with adding a test that highlights how the current behavior
  is broken. This is not mandatory since it is not always possible/approachable

## Guiding Principles

- We allow anyone to participate in our projects. Tasks can be carried out by anyone
  that demonstrates the capability to complete them
- Always be respectful of one another. Assume the best in others and act with empathy
  at all times
- Collaborate closely with individuals maintaining the project or experienced users.
  Getting ideas out in the open and seeing a proposal before it's a pull request helps
  reduce redundancy and ensures we're all connected to the decision-making process

## Releases

Noice is regularly updated on Google Play Store and F-Droid. A commit with the following
changes is necessary to prepare a new release.

1. It must bump app version name and code in `app/build.gradle`
2. It should add the release notes in `en-US` locale in the Fastlane metadata. Release notes are
   added to a new file at path `fastlane/metadata/android/en-US/changelogs/<version-code>.txt`
3. It should update the app's descriptive assets (Fastlane metadata) if necessary.

### Play Store

Noice uses [Travis CI](https://travis-ci.com/github/ashutoshgngwr/noice) for automatically
building and pushing releases to Google Play store. All releases happen in two stages.

- First, a candidate release is pushed to the beta track on the Play Store. Git tags for
  these are marked with format `0.0.0-rc`. This job pushes the new binary and its changelog
  to the Play Store.
- After ample time, the release candidates are promoted to production track on the Play
  Store. Git tags for these are marked with format `0.0.0`. This job promotes the latest
  beta release to the production track on the Play Store. It also updates the Fastlane metadata
  in the Play Store listing.

_**Note:** A beta release should be followed by its production release. If a new beta release
is created before the production release of the last beta release, the last beta release must
be manually promoted to the production track from the Play Console._

### F-Droid

F-Droid releases are picked by its builder based on the latest tag. F-Droid doesn't pick
the beta releases.
See [the metadata file](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.github.ashutoshgngwr.noice.yml)
for more information.
