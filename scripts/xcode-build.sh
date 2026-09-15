#!/bin/sh
# Xcode launched from Finder does not inherit a terminal's JAVA_HOME.
set -eu
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ]; then
    if [ -f .gradle/config.properties ]; then
        JAVA_HOME=$(sed -n 's/^java\.home=//p' .gradle/config.properties | head -1)
    fi
    if [ -z "${JAVA_HOME:-}" ]; then
        JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null) || {
            echo "error: Install JDK 21 or set java.home in .gradle/config.properties."
            exit 1
        }
    fi
fi
if [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "error: JAVA_HOME does not contain an executable bin/java. Check .gradle/config.properties."
    exit 1
fi
export JAVA_HOME
echo "Building the Kotlin framework (${CONFIGURATION:-Xcode configuration}). A Release optimization pass can take several minutes."
exec ./gradlew --console=plain :composeApp:embedAndSignAppleFrameworkForXcode
