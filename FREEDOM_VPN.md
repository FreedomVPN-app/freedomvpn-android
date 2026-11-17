# Freedom VPN для Android

Форк [v2rayNG](https://github.com/2dust/v2rayNG) (лицензия GPL-3.0) под сервис Topfreedom.

## Отличия от v2rayNG
- Пакет `com.topfreedom.vpn`, название «Freedom VPN», своя иконка. Ставится рядом с v2rayNG, не конфликтует.
- Ссылка `freedomvpn://install-sub?url=<URL-подписки, закодированный>#Freedom%20VPN`:
  - экран серверов открывается только после загрузки подписки;
  - повторное открытие той же ссылки обновляет подписку, а не выдаёт ошибку;
  - если сервер не выбран, выбирается первый в подписке (в панели первым стоит «⚡ Авто»).
- Подписка обновляется сама каждые 6 часов.
- User-Agent при загрузке подписки: `FreedomVPN/<версия>`.
- В APK вложены geo-файлы RoscomVPN (`geoip:direct`, `geosite:whitelist`, `category-ru` и др.) — те же, что Happ получает по заголовку routing. После каждого обновления приложения они перезаписываются.
- Проверка обновлений и раздел «О программе» смотрят на этот репозиторий; «Поддержка» → @topfreedom_support_bot, Telegram и пункт меню → @Topfreedom_bot.

## Сборка
GitHub Actions, workflow «Build Freedom VPN APK».
- Секреты репозитория: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD` (alias ключа — `freedomvpn`).
- Ручной запуск → тестовая сборка в Artifacts.
- Тег `v1.0.0` → релиз с файлами `FreedomVPN.apk` (универсальный), `*arm64-v8a*.apk`, `*armeabi-v7a*.apk`.
- Постоянная ссылка на скачивание: `https://github.com/<владелец>/<репозиторий>/releases/latest/download/FreedomVPN.apk`.

Ключ подписи терять нельзя: APK, подписанный другим ключом, не встанет поверх старого.

## Обновление из upstream
```
git remote add upstream https://github.com/2dust/v2rayNG.git
git fetch upstream master && git merge upstream/master
```
Конфликты возможны в изменённых файлах: build.gradle.kts, AppConfig.kt, AngConfigManager.kt, UrlSchemeActivity.kt, SettingsManager.kt, HttpUtil.kt, SubscriptionItem.kt, AndroidManifest.xml, strings.xml.
