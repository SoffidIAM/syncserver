@echo off
rem Licensed to the Apache Software Foundation (ASF) under one or more
rem contributor license agreements.  See the NOTICE file distributed with
rem this work for additional information regarding copyright ownership.
rem The ASF licenses this file to You under the Apache License, Version 2.0
rem (the "License"); you may not use this file except in compliance with
rem the License.  You may obtain a copy of the License at
rem
rem     http://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing, software
rem distributed under the License is distributed on an "AS IS" BASIS,
rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem See the License for the specific language governing permissions and
rem limitations under the License.

rem ---------------------------------------------------------------------------
rem Start/Stop Script for the Soffid Sync Server
rem
rem For supported commands call "soffid-sync.bat help" or see the usage section
rem towards the end of this file.
rem
rem Environment Variable Prerequisites
rem
rem   Do not set the variables in this script. Instead put them into a script
rem   setenv.bat in CATALINA_BASE/bin to keep your customizations separate.
rem
rem   WHEN RUNNING TOMCAT AS A WINDOWS SERVICE:
rem   Note that the environment variables that affect the behavior of this
rem   script will have no effect at all on Windows Services. As such, any
rem   local customizations made in a CATALINA_BASE/bin/setenv.bat script
rem   will also have no effect on Tomcat when launched as a Windows Service.
rem   The configuration that controls Windows Services is stored in the Windows
rem   Registry, and is most conveniently maintained using the "tomcat9w.exe"
rem   maintenance utility.
rem
rem   JAVA_OPTIONS   (Optional) Java runtime options used when the "start",
rem                   "run" or "debug" command is executed.
rem                   Include here and not in JAVA_OPTS all options, that should
rem                   only be used by Tomcat itself, not by the stop process,
rem                   the version command etc.
rem                   Examples are heap size, GC logging, JMX ports etc.
rem
rem   JAVA_HOME       Must point at your Java Development Kit installation.
rem                   Required to run the with the "debug" argument.
rem
rem   JRE_HOME        Must point at your Java Runtime installation.
rem                   Defaults to JAVA_HOME if empty. If JRE_HOME and JAVA_HOME
rem                   are both set, JRE_HOME is used.
rem
rem ---------------------------------------------------------------------------

setlocal

rem Suppress Terminate batch job on CTRL+C

set SYNC_BIN=%~dp0
set SYNC_HOME=%SYNC_BIN%\..
rem Ensure that any user defined CLASSPATH variables are not used on startup,
rem but allow them to be specified in setenv.bat, in rare case when it is needed.
set CLASSPATH=

rem Get standard environment variables
if not exist "%SYNC_BIN%\env.bat" goto setenvDone
call "%SYNC_BIN%\env.bat"
:setenvDone

if "%CLASSPATH%" == "" goto emptyClasspath
set "CLASSPATH=%CLASSPATH%;"
:emptyClasspath
set "CLASSPATH=%CLASSPATH%%SYNC_BIN%\bootstrap.jar;%SYNC_HOME%\lib\mariadb-java-client-1.8.0.jar;%SYNC_HOME%\lib\ojdbc7-12.1.0.1.0.jar;%SYNC_HOME%\lib\postgresql-42.2.5.jre7.jar;%SYNC_HOME%\lib\sqljdbc4-3.0.jar"

rem ----- Execute The Requested Command ---------------------------------------

echo Using SYNC_BASE:       "%SYNC_BASE%"
echo Using JAVA_HOME:       "%JAVA_HOME%"
echo Using CLASSPATH:       "%CLASSPATH%"

set MAINCLASS=com.soffid.iam.sync.bootstrap.SyncLoader

if NOT DEFINED JAVA_HOME goto noJavaHome
if not exist %JAVA_HOME%\bin\java.exe goto noJavaHome
set "JRE_HOME=%JAVA_HOME%"
goto okJava

:noJavaHome
java.exe %JAVA_OPTIONS% -classpath "%CLASSPATH%" %MAINCLASS% %*
goto exit

:okJava

rem Execute Java with the applicable properties
echo %JAVA_HOME%\bin\java.exe %JAVA_OPTIONS% -classpath "%CLASSPATH%" %MAINCLASS% %*
%JAVA_HOME%\bin\java.exe %JAVA_OPTIONS% -classpath "%CLASSPATH%" %MAINCLASS% %*
:exit
