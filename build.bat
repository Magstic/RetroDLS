@echo off
setlocal

cd /d "%~dp0"

if exist out rmdir /s /q out
if errorlevel 1 exit /b 1
if exist dist rmdir /s /q dist
if errorlevel 1 exit /b 1
mkdir out
if errorlevel 1 exit /b 1
mkdir dist
if errorlevel 1 exit /b 1
mkdir dist\lib
if errorlevel 1 exit /b 1

javac -encoding UTF-8 -cp lib\jlayer-1.0.1.jar -d out src\mobilebae\*.java
if errorlevel 1 exit /b 1

copy /y lib\jlayer-1.0.1.jar dist\lib\ >nul
if errorlevel 1 exit /b 1
copy /y lib\jlayer-1.0.1-sources.jar dist\lib\ >nul
if errorlevel 1 exit /b 1
copy /y lib\LICENSE-JLAYER.txt dist\lib\ >nul
if errorlevel 1 exit /b 1

jar cfm dist\retro-dls.jar manifest.mf -C out .
if errorlevel 1 exit /b 1

echo Built dist\retro-dls.jar
