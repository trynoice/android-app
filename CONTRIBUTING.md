# Contributing <!-- omit in toc -->

We welcome contributions of all kinds and sizes. This includes everything from
simple bug reports to large features.

## Table of Contents <!-- omit in toc -->

- [Workflow](#workflow)
- [Getting started](#getting-started)
- [Translations](#translations)
  - [Stats](#stats)
- [Adding sounds](#adding-sounds)
- [Contributing code](#contributing-code)
  - [Architecture](#architecture)
  - [Best Practices](#best-practices)
- [Guiding Principles](#guiding-principles)
- [Releases](#releases)
  - [Play Store](#play-store)
  - [F-Droid](#f-droid)

## Workflow

- We love GitHub issues!
- For small feature requests, an issue first proposing it for discussion or demo
  implementation in a PR suffice
- For big feature requests, please open an issue to avoid wasting time on a
  feature that might need reworking
- Small pull requests for things like typos, bug fixes, etc are always welcome

## Getting started

1. Ensure that you have the latest Android SDK tools installed on your machine
1. Fork the repository to create a copy in your GitHub account. The forked
   repository should be accessible at `https://github.com/<your-username>/noice`
1. Clone the forked repository to your machine
1. Open the existing project using Android Studio or any editor of your choice

## Translations

Please refer to the [Noice project on
Weblate](https://hosted.weblate.org/engage/noice/) to contribute translations.

### Stats

<p align="center">
  <img alt="translation stats" src="https://hosted.weblate.org/widgets/noice/-/horizontal-auto.svg" />
</p>

## Adding sounds

Feel free to add more sounds to Noice taking the following under consideration.

- Looping sounds should be at least 30 seconds and at most 2 minutes long. These
  are not hard limits but the goal should be to minimize the ease of recognising
  recurring patterns in loops
- If a looping sound doesn't have too many recognisable notes, e.g. cricket
  sound, consider using dual samples. Dual samples make it hard to recognise
  repeating patterns. See [this
  issue](https://github.com/trynoice/android-app/issues/62) for more details.
- Consider applying the [Compressor
  effect](https://en.wikipedia.org/wiki/Dynamic_range_compression#Controls_and_features)
  to the new sounds. We recommend the following settings, but these do not
  produce the best results for all ambient sounds. To get best results, tweak
  these settings, test their output and trust your best judgement.
  - Threshold: -32 dB
  - Noise Floor: -70 dB
  - Ratio: 10:1
  - Attack Time: 0.10 sec
  - Release Time: 1 sec
- All sounds should be encoded to `mp3` format with the following configuration.
  I use [Audacity](https://www.audacityteam.org) for editing audio
  - Sample rate: 44.1 kHz
  - Bit rate mode: Average
  - Quality: Standard, 192 kbps
  - Variable Speed: Fast
  - Channel Mode: Stereo
- Once you have the sounds ready, copy the audio files to the
  [`assets`](https://github.com/trynoice/android-app/tree/HEAD/app/src/main/assets)
  directory and add them to the `LIBRARY` map in
  [`Sound.kt`](https://github.com/trynoice/android-app/blob/HEAD/app/src/main/java/com/github/trynoice/android-app/sound/Sound.kt),
  e.g.

  ```kotlin
  "birds" to Sound(
    // relative path of audio files in `assets` directory
    arrayOf("birds_0.mp3", "birds_1.mp3"),

    // title string resource
    R.string.birds,

    // sound group in which the sound should be placed
    R.string.sound_group__life,

    // an array of Pair instances where first string resource represents the description
    // of sound and second string resource represents the URL of sound's source. Both of
    // these resources are shown on the `About` screen of app.
    arrayOf(
      Pair(
        R.string.credits__sound_birds,
        R.string.credits__sound_birds_url
      )
    )
  )
  ```

## Contributing code

Most of the code is documented but not very thoroughly.

### Architecture

The Android app is written in Kotlin. It does not adhere to modern architectures
(e.g. MVVM) or use modern frameworks. The rationale behind the choice was simple
&mdash; the view interactions are simple, and the views don't mutate too often.

The following diagram depicts the sound engine architecture in detail. Here
`MediaPlayerService` (a foreground service) controls a `PlayerManager` instance.
A `PlayerManager` controls multiple `Player` instances and can have at most one
`Player` instance for each sound present in the library.

<p align="center">
  <img align="center" alt="Android app architecture" src="graphics/android-app-architecture.svg" /><br>
</p>

### Best Practices

- Let Android Studio do the code formatting
- Add comments wherever you deem them necessary
- Include tests when adding new features
- When fixing bugs, start with adding a test that highlights how the current behaviour
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
3. It should update the app's descriptive assets (Fastlane metadata) if required
4. It should update generated screenshots if required (`fastlane generate_screenshots`)

### Play Store

Noice uses [GitHub actions](https://github.com/trynoice/android-app/actions) to automatically
build and push releases to Google Play store.

- We have been strictly following [Semantic Versioning](https://semver.org) since 0.4.x
- New translations are considered as a feature
- All releases tagged in Git repository are pushed to beta track on the Play Store. After ample
  time, a release is manually promoted to the production track for general availability.
- In case a feature is merged into the master branch and a patch release needs to be created for
  the latest public release, create a temporary branch with pattern `Major.Minor.x` e.g., `1.1.x`.
  Tag any further patch releases to a commit in this branch. Before the next _non-patch_ release,
  the temporary branch can be merged into master.

### F-Droid

F-Droid releases are picked by its builder based on the latest tag. See
[the metadata file](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.github.ashutoshgngwr.noice.yml)
for more information.
