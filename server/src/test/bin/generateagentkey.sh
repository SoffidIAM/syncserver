#!/bin/bash
BASEDIR=$(dirname $0)/../../..
TARGETDIR=$BASEDIR/target
TESTDIR=$TARGETDIR/agent-test
CLASSPATH=$TARGETDIR/classes
mkdir -p $TESTDIR/conf
for i in $TESTDIR/lib/*.jar
do
   CLASSPATH=$CLASSPATH:$i
done
$JAVA_HOME/bin/java -cp $CLASSPATH -Dexe4j.moduleName=$TESTDIR/bin/test es.caib.seycon.config.Config $*
