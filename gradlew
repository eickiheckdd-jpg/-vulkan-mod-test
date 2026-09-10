#!/bin/sh

# Gradle wrapper script for VulkanMod
# Locate JAVA_HOME/JAVACMD, then bootstrap via gradle-wrapper.jar

set -e

# Resolve APP_HOME
PRG="$0"
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
APP_HOME=$(cd "${PRG%/*}" && pwd -P)

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Locate Java
if [ -z "$JAVA_HOME" ] ; then
    if [ -x /usr/lib/jvm/default-java/bin/java ] ; then
        JAVA_HOME=/usr/lib/jvm/default-java
    elif [ -x /usr/lib/jvm/java-21-openjdk-amd64/bin/java ] ; then
        JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
    elif [ -x /usr/lib/jvm/java-17-openjdk-amd64/bin/java ] ; then
        JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
    elif [ -x /usr/lib/jvm/java-11-openjdk-amd64/bin/java ] ; then
        JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
    fi
fi

if [ -z "$JAVA_HOME" ] ; then
    JAVACMD=java
else
    JAVACMD="$JAVA_HOME/bin/java"
fi

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
