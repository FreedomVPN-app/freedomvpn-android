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
- HWID для лимита устройств Remnawave: при загрузке любой подписки отправляются `x-hwid`, `x-device-os`, `x-ver-os`, `x-device-model` (код `freedom/DeviceId.kt`). `x-hwid` — 32 hex-символа, хэш ANDROID_ID и домена подписки: стабилен для устройства и не совпадает у разных сервисов. Свои заголовки подписки (requestHeaders) их перекрывают.
- В APK вложены geo-файлы RoscomVPN (`geoip:direct`, `geosite:whitelist`, `category-ru` и др.) — те же, что Happ получает по заголовку routing. После каждого обновления приложения они перезаписываются.
- «Пригласить» (главный экран и карточка подписки) открывает `freedom/InviteActivity.kt`: GET /cabinet/referral → QR-код ссылки-приглашения (ссылка кабинета, запасная — ссылка бота), «Поделиться», «Скопировать», счётчик приглашённых, «Бонусы и подробности в личном кабинете» → /referral кабинета.
- Инструкция для iPhone/iPad (через Happ): `landing/ios/index.html` → https://cabinet.topfreedomhub.com/ios/ (файл cabinet-dist/ios/index.html, в nginx кабинета `location /ios/` рядом с `/android/`, выложено 21.11).
- Общая страница выбора устройства: `landing/apps/index.html` → https://cabinet.topfreedomhub.com/apps/ (cabinet-dist/apps/index.html, `location /apps/` в nginx кабинета, выложено 21.11). Своё устройство определяется по User-Agent и ставится первым; на /android/ и /ios/ ссылка «Другие устройства».
- Инструкция для компьютера (Windows и macOS через Happ, вкладки, Mac определяется по User-Agent): `landing/pc/index.html` → https://cabinet.topfreedomhub.com/pc/ (cabinet-dist/pc/index.html, `location /pc/` в nginx кабинета, 21.11).
- Раз в сутки приложение само проверяет последний релиз на GitHub и присылает уведомление «Доступна версия Freedom VPN X» (по разу на версию, канал «Обновления приложения»; код `freedom/UpdateNotifier.kt`), нажатие открывает экран обновления.
- Главный экран (`ui/main/MainHome.kt`, `MainHomeNavigation.kt`): карточка подключения с круглой кнопкой (после подключения карточка окрашивается в акцентный цвет, статус «Защищено · сервер»), строка подписки (дни, дата окончания, трафик, «Пригласить» для подписки Freedom), список серверов подписки с флагом, пингом и галочкой у выбранного. Снизу панель «Главная / Серверы / Аккаунт / Ещё»; «Ещё» — меню с настройками, приложениями через VPN, кабинетом, журналом, резервной копией, проверкой обновлений и «О приложении». Боковое меню осталось только на полном списке серверов. Тема (системная/светлая/тёмная) — в настройках.
- Проверка обновлений и раздел «О программе» смотрят на этот репозиторий; «Поддержка» → @topfreedom_support_bot, Telegram и пункт меню → @Topfreedom_bot.

## Вход в аккаунт (Telegram, Google, Яндекс)
Код: `V2rayNG/app/src/main/java/com/v2ray/ang/freedom/`. Адреса — в `FreedomConfig.kt`.
- Экран входа показывается при первом запуске (пока нет подписок) и из меню («Аккаунт»).
- Работает через API кабинета Bedolaga (`https://cabinet.topfreedomhub.com/api/cabinet`):
  вход → если подписки нет, включается пробный период → ссылка на подписку → импорт → выбран «⚡ Авто».
- Telegram: `/auth/deeplink/request` → бот `?start=webauth_<token>` → `/auth/deeplink/poll`. Бот подтверждает вход только
  зарегистрированным пользователям: новому нужно сначала нажать «Старт».
- Google/Яндекс: `/auth/oauth/<provider>/authorize` → браузер → `CABINET_URL/auth/oauth/callback` (Android App Link)
  → `/auth/oauth/<provider>/callback`. Нужен `server/assetlinks.json` по адресу
  `https://cabinet.topfreedomhub.com/.well-known/assetlinks.json`. Колбэки, начатые не приложением (вход в веб-кабинет
  в браузере на этом же телефоне), приложение передаёт в браузер.

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
Конфликты возможны в изменённых файлах: build.gradle.kts, AppConfig.kt, AngConfigManager.kt, MainActivity.kt, MainDrawer.kt, UrlSchemeActivity.kt, SettingsManager.kt, HttpUtil.kt, SubscriptionItem.kt, AndroidManifest.xml, strings.xml.
