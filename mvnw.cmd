@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET "BASE_DIR=%~dp0") ELSE (SET "BASE_DIR=%__MVNW_ARG0_NAME__%")

@SET MAVEN_PROJECTBASEDIR=%BASE_DIR%

@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

@SET WRAPPER_PROPERTIES=%BASE_DIR%.mvn\wrapper\maven-wrapper.properties
@SET WRAPPER_URL=
@FOR /F "usebackq tokens=1,2 delims==" %%A IN ("%WRAPPER_PROPERTIES%") DO (
    @IF "%%A"=="wrapperUrl" (
        @SET WRAPPER_URL=%%B
    )
)

@SET DOWNLOAD_URL=
@FOR /F "usebackq tokens=1,2 delims==" %%A IN ("%WRAPPER_PROPERTIES%") DO (
    @IF "%%A"=="distributionUrl" (
        @SET DOWNLOAD_URL=%%B
    )
)

@SET M2_HOME=
@SET JAVA_HOME_VAR=%JAVA_HOME%
@IF "%JAVA_HOME_VAR%"=="" (
    @SET JAVA_EXEC=java
) ELSE (
    @SET JAVA_EXEC="%JAVA_HOME_VAR%\bin\java"
)

@SET MAVEN_OPTS=%MAVEN_OPTS% --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED

@SET WRAPPER_JAR=%BASE_DIR%.mvn\wrapper\maven-wrapper.jar

@IF EXIST "%WRAPPER_JAR%" GOTO runWithJar

@SET MVNW_REPOURL=https://repo.maven.apache.org/maven2
@IF "%MVNW_REPOURL%"=="" (@SET MVNW_REPOURL=https://repo.maven.apache.org/maven2)
@SET WRAPPER_JAR_URL=%MVNW_REPOURL%/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar

@ECHO Downloading %WRAPPER_JAR_URL%
powershell -Command "(New-Object Net.WebClient).DownloadFile('%WRAPPER_JAR_URL%', '%WRAPPER_JAR%')"
@IF NOT "%ERRORLEVEL%"=="0" GOTO error

:runWithJar
@%JAVA_EXEC% %MAVEN_OPTS% -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" %WRAPPER_LAUNCHER% %MAVEN_CONFIG% %*
@IF NOT "%ERRORLEVEL%"=="0" GOTO error
@GOTO end

:error
@SET ERROR_CODE=%ERRORLEVEL%
@ECHO.
@ECHO Error executing Maven command.
@EXIT /B %ERROR_CODE%

:end
@EXIT /B 0
