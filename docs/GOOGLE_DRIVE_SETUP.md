# Настройка Google Drive OAuth 2.0

Для работы синхронизации с Google Drive на реальном устройстве (Samsung Galaxy S26 Ultra) необходимо один раз зарегистрировать OAuth 2.0 Client ID для Android в Google Cloud Console. Пароли и секреты в приложение не вшиваются.

## Шаги настройки
1. Откройте [Google Cloud Console](https://console.cloud.google.com/) и создайте проект (например, `Personal Kanban`).
2. Перейдите в **APIs & Services → Library**, найдите **Google Drive API** и нажмите **Enable**.
3. Перейдите в **APIs & Services → OAuth consent screen**:
   - Выберите **External**, заполните название приложения (`Personal Kanban`) и ваш email.
   - Добавьте scope: `https://www.googleapis.com/auth/drive.file`.
   - В разделе **Test users** добавьте ваш Google-аккаунт (пока приложение в статусе Testing).
4. Перейдите в **APIs & Services → Credentials → Create Credentials → OAuth client ID**:
   - Application type: **Android**.
   - Package name: `com.personalkanban.app` (для debug-сборки — `com.personalkanban.app.debug`).
   - SHA-1 certificate fingerprint: укажите SHA-1 вашего ключа подписи (узнать можно командой `keytool -list -v -keystore your.keystore`).
5. Для автоматической подписи релизов в GitHub Actions добавьте в **Settings → Secrets and variables → Actions** репозитория:
   - `RELEASE_KEYSTORE_BASE64` — base64-строка вашего `.keystore` файла.
   - `RELEASE_KEYSTORE_PASSWORD` — пароль хранилища ключей.
   - `RELEASE_KEY_ALIAS` — alias ключа.
   - `RELEASE_KEY_PASSWORD` — пароль ключа.
