#!/bin/bash
BASEDIR=$(dirname $0)/../../..
TARGETDIR=$BASEDIR/target
TESTDIR=$TARGETDIR/server-test
CLASSPATH=$TARGETDIR/classes
for i in $TESTDIR/lib/*.jar
do
   CLASSPATH=$CLASSPATH:$i
done
export CLASSPATH
$JAVA_HOME/bin/java -Dseycon.server.url=//localhost:1586/seycon/Server es.caib.util.ChangePassword $*
