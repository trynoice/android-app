#!/usr/bin/env bash

# In summary, this script does the following.
# 1. Build and sign the full APK variant.
# 2. Remove non-free dependencies from Gradle scripts, and then build and sign
#    the free variant.
# 3. Move output files to the specified destination paths.

if [ -z "$JKS_STORE" ] || [ -z "$STORE_PASSWORD" ] || [ -z "$KEY_ALIAS" ] \
  || [ -z "$KEY_PASSWORD" ] || [ -z "$FREE_APK" ] || [ -z "$FREE_MAPPING" ] \
  || [ -z "$FULL_APK" ] || [ -z "$FULL_MAPPING" ];
then
  printf "missing at least one of the following required env variables!\n\n"
  printf "JKS_STORE\tbase64 encoded java keystore for signing apks\n"
  printf "STORE_PASSWORD\tkeystore's password\n"
  printf "KEY_ALIAS\tsigning alias\n"
  printf "KEY_PASSWORD\tpassword for the signing alias\n"
  printf "FULL_APK\toutput path for full variant apk\n"
  printf "FULL_MAPPING\toutput path for mapping.txt of the full variant apk\n"
  printf "FREE_APK\toutput path for free variant apk\n"
  printf "FREE_MAPPING\toutput path for mapping.txt of the free variant apk\n"
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

# build full variant first
echo "building full apk variant..."
./gradlew assembleFullRelease --stacktrace
GRADLE_EXITCODE_FULL=$?

# remove non-free deps from the free build but keep the signingConfig
echo "removing non-free dependencies from gradle build scripts..."
sed -i -r -e 's@^(.*)signingConfig(.*)\/\/.*$@\1signingConfig\2@' app/build.gradle
sed -i -e '/sed:free-build:remove/d' build.gradle app/build.gradle

# build free variant
echo "building free apk variant..."
./gradlew assembleFreeRelease --stacktrace
GRADLE_EXITCODE_FREE=$?

# revert gradle files back to their original state
echo "restoring gradle build scripts to their original state..."
git checkout -- build.gradle app/build.gradle

if [ $GRADLE_EXITCODE_FREE -ne 0 ];  then
  printf "\nfailed to build free variant\n" >&2
  exit 1
fi

if [ $GRADLE_EXITCODE_FULL -ne 0 ];  then
  printf "\nfailed to build full variant\n" >&2
  exit 1
fi

# move files to destination paths
echo "moving artifacts to specified destination paths..."
mv -vf app/build/outputs/apk/full/release/app-full-release.apk "$FULL_APK"
mv -vf app/build/outputs/mapping/fullRelease/mapping.txt "$FULL_MAPPING"
mv -vf app/build/outputs/apk/free/release/app-free-release.apk "$FREE_APK"
mv -vf app/build/outputs/mapping/freeRelease/mapping.txt "$FREE_MAPPING"
