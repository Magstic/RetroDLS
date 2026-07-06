@echo off
setlocal

cd /d "%~dp0"

if exist out rmdir /s /q out
if exist dist rmdir /s /q dist
mkdir out
mkdir dist

javac -encoding UTF-8 -d out src\mobilebae\*.java
if errorlevel 1 exit /b 1

jar cfe dist\retro-dls.jar mobilebae.MobileBae -C out .
if errorlevel 1 exit /b 1

echo Built dist\retro-dls.jar
