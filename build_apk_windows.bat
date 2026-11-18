@echo off
chcp 65001 >nul
echo ========================================================
echo Сборка установочного файла StreamBoxTV (app-debug.apk)
echo ========================================================
where gradle >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [!] Gradle не найден в PATH. Откройте эту папку в Android Studio:
    echo     Build -^> Build Bundle(s) / APK(s) -^> Build APK(s)
    pause
    exit /b 1
)
call gradle assembleDebug
echo.
echo Готовый APK находится в папке: app\build\outputs\apk\debug\app-debug.apk
pause
