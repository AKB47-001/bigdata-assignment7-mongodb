@echo off

REM Define the classpath
set CP=.;lib/mongodb-driver-sync-4.5.0.jar;lib/mongodb-driver-core-4.5.0.jar;lib/bson-4.5.0.jar

REM Compile the Java file
echo Compiling MongoDB.java...
javac -cp "%CP%" src/MongoDB.java -d src

REM Run the compiled code
echo Running MongoDB program...
java -cp "src;%CP%" MongoDB