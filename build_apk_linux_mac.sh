#!/usr/bin/env bash
set -e
echo "========================================================"
echo "Сборка установочного файла StreamBoxTV (app-debug.apk)"
echo "========================================================"
if ! command -v gradle >/dev/null 2>&1; then
  echo "[!] Gradle не найден в PATH. Откройте папку в Android Studio: Build -> Build APK(s)"
  exit 1
fi
gradle assembleDebug
echo "Готовый APK: app/build/outputs/apk/debug/app-debug.apk"
