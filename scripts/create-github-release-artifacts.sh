#!/usr/bin/env bash

# In summary, this script does the following.
# 1. Build and sign the play store APK variant.
# 2. Remove non-free dependencies from Gradle scripts, and then build and sign
#    the f-droid variant.
# 3. Move output files to the specified destination paths.

if [ -z "$JKS_STORE" ] || [ -z "$STORE_PASSWORD" ] || [ -z "$KEY_ALIAS" ] \
  || [ -z "$KEY_PASSWORD" ] || [ -z "$FDROID_APK" ] || [ -z "$FDROID_MAPPING" ] \
  || [ -z "$PLAYSTORE_APK" ] || [ -z "$PLAYSTORE_MAPPING" ];
then
  printf "missing at least one of the following required env variables!\n\n"
  printf "JKS_STORE\t\tbase64 encoded java keystore for signing apks\n"
  printf "STORE_PASSWORD\t\tkeystore's password\n"
  printf "KEY_ALIAS\t\tsigning alias\n"
  printf "KEY_PASSWORD\t\tpassword for the signing alias\n"
  printf "PLAYSTORE_APK\t\toutput path for play store variant apk\n"
  printf "PLAYSTORE_MAPPING\toutput path for mapping.txt of the play store variant apk\n"
  printf "FDROID_APK\t\toutput path for f-droid variant apk\n"
  printf "FDROID_MAPPING\t\toutput path for mapping.txt of the f-droid variant apk\n"
  exit 1
fi

function cleanup() {
  echo "stopping gradle daemon..."
  ./gradlew --stop
  rm -f keystore.jks
}

trap cleanup EXIT
trap exit INT

# decode java keystore
echo "$JKS_STORE" | base64 --decode > keystore.jks

# build playstore variant first
./gradlew assemblePlaystoreRelease --stacktrace
GRADLE_EXITCODE_PLAYSTORE=$?

# remove non-free deps from the f-droid build but keep the signingConfig
sed -i -r -e 's@^(.*)signingConfig(.*)\/\/.*$@\1signingConfig\2@' app/build.gradle
sed -i -e '/sed:fdroid-build:remove/d' build.gradle app/build.gradle

# build f-droid variant
./gradlew assembleFdroidRelease --stacktrace
GRADLE_EXITCODE_FDROID=$?

# revert gradle files back to their original state
git checkout -- build.gradle app/build.gradle

if [ $GRADLE_EXITCODE_FDROID -ne 0 ];  then
  printf "\nfailed to build f-droid variant\n" >&2
  exit 1
fi

if [ $GRADLE_EXITCODE_PLAYSTORE -ne 0 ];  then
  printf "\nfailed to build play store variant\n" >&2
  exit 1
fi

# move files to destination paths
mv -vf app/build/outputs/apk/playstore/release/app-playstore-release.apk "$PLAYSTORE_APK"
mv -vf app/build/outputs/mapping/playstoreRelease/mapping.txt "$PLAYSTORE_MAPPING"
mv -vf app/build/outputs/apk/fdroid/release/app-fdroid-release.apk "$FDROID_APK"
mv -vf app/build/outputs/mapping/fdroidRelease/mapping.txt "$FDROID_MAPPING"
