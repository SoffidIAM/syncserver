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
$JAVA_HOME/bin/java -Dseycon.properties=$TARGETDIR/test-classes/seycon.properties -Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=4001,suspend=n es.caib.seycon.ServerApplication
