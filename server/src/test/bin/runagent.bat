set BASE=c:\seycon
SET JAVA_HOME=c:\j2sdk1.4.2_17
set LOTUS_HOME="C:\Archivos de programa\IBM\Lotus\Notes"
set LOTUS_JAR_HOME=%LOTUS_HOME%
SET SYSTEM_DLL="C:\WINDOWS\system32"
PATH .;%LOTUS_JAR_HOME%;%JAVA_HOME%\bin;%PATH%;%LOTUS_HOME%;%LOTUS_HOME%\icc;%SYSTEM_DLL%
SET CLASSPATH=%BASE%\classes;%LOTUS_JAR_HOME%\Notes.jar;%LOTUS_HOME%;%LOTUS_HOME%\icc;%SYSTEM_DLL%
%JAVA_HOME%\bin\java -cp %CLASSPATH% -Djava.library.path=%LOTUS_HOME%;%LOTUS_HOME%\icc;%SYSTEM_DLL% -Dseycon.properties=%BASE%\resources\seycon.properties -Djavax.net.debug=seycon es.caib.seycon.ClientApplication