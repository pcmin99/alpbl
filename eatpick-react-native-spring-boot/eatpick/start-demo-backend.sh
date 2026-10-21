#!/bin/sh
set -eu
cd "$(dirname "$0")"
exec java -jar backend/eatpick-api.jar --spring.profiles.active=demo
