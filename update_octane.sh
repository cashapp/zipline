#!/bin/bash

set -e

ASSET_LOCATION=sample-octane/src/main/assets/octane

# Download and extract ZIP from GitHub.
wget https://github.com/chromium/octane/archive/master.zip
unzip master.zip
rm master.zip

# Copy all JS files to asset directory.
rm $ASSET_LOCATION/*.js
cp octane-master/*.js $ASSET_LOCATION
rm -r octane-master

# Remove node-based runner.
rm $ASSET_LOCATION/run.js
# Remove typescript test.
rm $ASSET_LOCATION/typescript*.js
