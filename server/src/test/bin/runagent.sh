#!/bin/bash
BASEDIR=$(dirname $0)/../../..
TARGETDIR=$BASEDIR/target
TARGETDIR=target
TESTDIR=$TARGETDIR/agent-test
CLASSPATH=$TARGETDIR/classes
rm $TESTDIR/lib/seycon-library*jar
for i in $TESTDIR/lib/*.jar
do
   CLASSPATH=$CLASSPATH:$i
done
export CLASSPATH
cp target/seycon-library.jar $TESTDIR/lib
#$JAVA_HOME/bin/java -Xdebug  -Dexe4j.moduleName=$TESTDIR/bin/test -Xrunjdwp:transport=dt_socket,server=y,address=4002,suspend=n es.caib.seycon.SeyconApplication
$JAVA_HOME/bin/java -Dexe4j.moduleName=$TESTDIR/bin/test es.caib.seycon.bootstrap.SeyconLoader $*
