#!/bin/bash
BASEDIR=$(dirname $0)/../../..
TARGETDIR=$BASEDIR/target
TESTDIR=$TARGETDIR/server-test
CLASSPATH=$TARGETDIR/classes
mkdir -p $TESTDIR/conf
mkdir -p $TESTDIR/lib
for i in $TARGETDIR/agent-test/lib/*.jar $TARGETDIR/server-only/lib/*.jar
do
   CLASSPATH=$CLASSPATH:$i
done
$JAVA_HOME/bin/java -cp $CLASSPATH -Dexe4j.moduleName=$TESTDIR/bin/test es.caib.seycon.ng.sync.bootstrap.DumpDB $*
