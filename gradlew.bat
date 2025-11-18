@echo off
if exist "gradle\wrapper\gradle-wrapper.jar" (
  java -jar "gradle\wrapper\gradle-wrapper.jar" %*
) else (
  gradle %*
)
