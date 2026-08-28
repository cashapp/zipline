#!/system/bin/sh
#
# This script allows printf to work from native code by enabling stdout/stderr.
# See https://developer.android.com/ndk/guides/wrap-script for more info.

exec "$@"
