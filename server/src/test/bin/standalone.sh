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
rm $TESTDIR/lib/seycon-library*	
cp target/seycon-library.jar $TESTDIR/lib/seycon-library.jar
if [[ "$1" == "-d" ]]
then
   sudo -E $JAVA_HOME/bin/java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=1234,suspend=y -Dexe4j.moduleName=$TESTDIR/bin/test es.caib.seycon.ng.sync.SeyconApplication 
else
    sudo -E $JAVA_HOME/bin/java -Dexe4j.moduleName=$TESTDIR/bin/test es.caib.seycon.ng.sync.SeyconApplication $*
fi
