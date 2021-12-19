@echo off

:: BatchGotAdmin
:-------------------------------------
REM  --> Check for permissions
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"

REM --> If error flag set, we do not have admin.
if '%errorlevel%' NEQ '0' (
    echo Requesting administrative privileges...
    goto UACPrompt
) else ( goto gotAdmin )

:UACPrompt
    echo Set UAC = CreateObject^("Shell.Application"^) > "%temp%\getadmin.vbs"
    echo UAC.ShellExecute "%~s0", "", "", "runas", 1 >> "%temp%\getadmin.vbs"

    "%temp%\getadmin.vbs"
    exit /B

:gotAdmin
    if exist "%temp%\getadmin.vbs" ( del "%temp%\getadmin.vbs" )
    pushd "%CD%"
    CD /D "%~dp0"
:--------------------------------------

net stop "Windows Time"

w32tm /config "/manualpeerlist:pool.ntp.org 0.pool.ntp.org 1.pool.ntp.org 2.pool.ntp.org 3.pool.ntp.org cn.pool.ntp.org 0.cn.pool.ntp.org 1.cn.pool.ntp.org 2.cn.pool.ntp.org 3.cn.pool.ntp.org"

net start "Windows Time"

sc config w32time start= auto
