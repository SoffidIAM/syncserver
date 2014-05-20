#!/bin/bash
BASEDIR=$(dirname $0)/../../..
TARGETDIR=$BASEDIR/target
TESTDIR=$TARGETDIR/server-test
CLASSPATH=$TARGETDIR/classes:$TARGETDIR/test-classes
for i in $TESTDIR/lib/*.jar
do
   CLASSPATH=$CLASSPATH:$i
done
export CLASSPATH
$JAVA_HOME/bin/java -Dseycon.server.url=https://localhost:10500/seycon/Server/logon bubu.test.seycon.Stress $*
