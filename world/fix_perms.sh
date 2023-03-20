#!/bin/bash 

# Loads cert/key into container as environment variable so
# we can write it as postgres user in the container
#
# Bind mounting it from host on non-native Docker environments
# (e.g., OS X) leads to permission issues.

echo "$SERVER_CERT" > /var/lib/postgresql/server.crt
echo "$SERVER_KEY" > /var/lib/postgresql/server.key

cat /var/lib/postgresql/server.crt
cat /var/lib/postgresql/server.key

chmod 600 /var/lib/postgresql/server.key
chmod 600 /var/lib/postgresql/server.crt