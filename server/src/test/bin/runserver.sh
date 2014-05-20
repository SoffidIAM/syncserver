#!/bin/bash
BASEDIR=$(dirname $0)/../../..
TARGETDIR=$BASEDIR/target
TESTDIR=$TARGETDIR/server-test
CLASSPATH=$TARGETDIR/classes
mkdir -p $TESTDIR/lib
rm $TESTDIR/lib/seycon-library*	
for i in $TARGETDIR/syncserver.jar $TARGETDIR/agent-test/lib/*.jar $TARGETDIR/server-only/lib/*.jar
do
   cp $i $TESTDIR/lib
   CLASSPATH=$CLASSPATH:$i
done
export CLASSPATH
#$JAVA_HOME/bin/java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=2345,suspend=y es.caib.seycon.ng.sync.bootstrap.SeyconLoader 
sudo -E $JAVA_HOME/bin/java -Dexe4j.moduleName=$TESTDIR/bin/test es.caib.seycon.ng.sync.bootstrap.SeyconLoader $*
