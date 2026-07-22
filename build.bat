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

javac -encoding UTF-8 -d out src\mobilebae\*.java
if errorlevel 1 exit /b 1

jar cfe dist\retro-dls.jar mobilebae.MobileBae -C out .
if errorlevel 1 exit /b 1

echo Built dist\retro-dls.jar
