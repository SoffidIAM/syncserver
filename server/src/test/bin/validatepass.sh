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
echo "VIA RMI"
$JAVA_HOME/bin/java -Dseycon.server.url=//localhost:1586/seycon/Server es.caib.util.ValidatePassword $*
echo "VIA HTTP"
$JAVA_HOME/bin/java -Dseycon.server.url=https://localhost:10500/seycon/Server/logon es.caib.util.ValidatePassword $*
