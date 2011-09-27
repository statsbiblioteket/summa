#!/bin/bash

set -e
DEPLOY=`dirname $0`/..

#
# EDIT HERE
# Variables prepended with ### are suitable for replacement
# by autogenerated scripts
#

###MAINJAR=
export MAINJAR=@summa.ilib.search.api@
export MAINCLASS=dk.statsbiblioteket.summa.search.api.tools.SearchTool

export PRINT_CONFIG=
export DEFAULT_CONFIGURATION=$DEPLOY/config/didyoumean-tool.configuration.xml
export LOG4J=tools.log4j.xml
###LIBDIRS=
###JAVA_HOME=
export JVM_OPTS="$JVM_OPTS -Xmx64m "
export SECURITY_POLICY="$DEPLOY/config/server.policy"
###ENABLE_JMX=

###JMX_PORT=
###JMX_SSL=
###JMX_ACCESS=
###JMX_PASS=

# All is ready, execute!
exec $DEPLOY/bin/generic_start.sh "$@"