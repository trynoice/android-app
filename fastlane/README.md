fastlane documentation
================
# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```
xcode-select --install
```

Install _fastlane_ using
```
[sudo] gem install fastlane -NV
```
or alternatively using `brew install fastlane`

# Available Actions
## Android
### android upload_metadata
```
fastlane android upload_metadata
```
Upload metadata to Google Play Store
### android beta
```
fastlane android beta
```
Deploy a new version to the beta track on the Google Play
### android generate_screenshots
```
fastlane android generate_screenshots
```
Generate screenshots of locales for which metadata exists

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.
More information about fastlane can be found on [fastlane.tools](https://fastlane.tools).
The documentation of fastlane can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
