# Noice

[![Build Status](https://travis-ci.org/ashutoshgngwr/noice.svg?branch=master)](https://travis-ci.org/ashutoshgngwr/noice)
[![codecov](https://codecov.io/gh/ashutoshgngwr/noice/branch/master/graph/badge.svg)](https://codecov.io/gh/ashutoshgngwr/noice)
[![GitHub tag](https://img.shields.io/github/tag/ashutoshgngwr/noice.svg)](https://GitHub.com/ashutoshgngwr/noice/tags/)
[![Releases](https://img.shields.io/badge/android-5.0%2B-blue.svg)][google-play-link]
[![GitHub license](https://img.shields.io/github/license/ashutoshgngwr/noice.svg)](https://github.com/ashutoshgngwr/noice/blob/master/LICENSE)

![Feature graphic](graphics/feature.png)

## Download

[![Get it on Google Play](https://i.imgur.com/g9vve1f.png)][google-play-link]

## Features

- 18 recorded noises
- Make customised mix
- Play alongside other music players
- Individual volume control for each noise source
- Offline playback

## Screenshots

![Screenshot 1](graphics/screen-1.png)
![Screenshot 2](graphics/screen-2.png)
![Screenshot 3](graphics/screen-3.png)

## Contributing

We welcome contributions of all kinds and sizes. This includes everything from simple bug reports to large features.

### Adding sounds

Android [SoundPool](https://developer.android.com/reference/android/media/SoundPool) has strict limitations on sound size. Please use < 10 seconds sounds at 32 kHz sample rate or < 15 seconds at 22 kHz. Test all sound changes to make sure if it is played correctly. Sounds might be chopped off if memory limits are exceeded.

Lower sample rates will allow longer playbacks but it is not recommended.

### Workflow

We love GitHub issues!

For small feature requests, an issue first proposing it for discussion or demo implementation in a PR suffice.

For big feature requests, please open an issue to avoid wasting time on a feature that might need reworking.

Small pull requests for things like typos, bug fixes, etc are always welcome.

### DOs

- Let Android Studio do the code formatting.

- Include tests when adding new features. When fixing bugs, start with adding a test that highlights how the current behavior is broken.

## Privacy Policy

Noice does not collect any kind of data from users. Heck, it doesn't even connect to the Internet.

## License

[MIT](LICENSE)

[google-play-link]: https://play.google.com/store/apps/details?id=com.github.ashutoshgngwr.noice
