# CopilotMoneyAndroid

> **Unofficial personal project.** Not affiliated with, endorsed by, or connected to Copilot Money, Inc.
> "Copilot Money" is a trademark of its owner. No code or assets of the original app are used;
> the UI is recreated from publicly available screenshots.

Рабочее название. Приложение для личных финансов под Android (в самом приложении — «Money Tracker»),
по UX и визуальному языку повторяющее Copilot Money (iOS). Делается для себя; перед публикацией
в сторе сменить название и айдентику — см. «Перед публикацией в стор».

## Что уже сделано

- Каркас: шапка, лента разделов с выбранным пунктом по центру, свайп между разделами.
- **Dashboard**: остаток бюджета и линия расходов против ожидаемого темпа (регулярные платежи
  учитываются в дни списания), блок «To review» с отметкой просмотренного, кольца бюджетов.
- **Transactions**: лента по дням — категория, счёт, сумма, отметка непросмотренных.
  Кнопка «+» добавляет транзакцию вручную, нажатие на транзакцию открывает правку и удаление.
  Дата — в пределах прошлого и текущего месяца: более ранние экраны пока не загружают.
- **Вход и облако**: Firebase Authentication (Google и email) и Firestore. Первый вход создаёт
  стартовые категории, кнопка *Load demo data* в настройках (шестерёнка) заливает демо-транзакции.
- Без `app/google-services.json` приложение работает в демо-режиме на данных в памяти.
- Остальные разделы (Cash flow, Accounts, Investments, Categories, Goals, Recurrings) — заглушки.

## Устройство

| Путь | Что там |
| --- | --- |
| `data/model` | Модели. Деньги — `Money` в центах, не `Double` |
| `data/demo` | Демо-данные за прошлый и текущий месяц относительно сегодняшней даты |
| `data/auth`, `data/firebase` | Вход (Credential Manager + Firebase Auth) и Firestore |
| `data/AppGraph.kt` | Выбор реализаций: облако или демо |
| `feature/*` | Экраны. Состояние и формы считаются чистыми функциями и покрыты тестами |
| `ui/theme` | Все цвета, шрифты и формы. Смена айдентики — правка этих файлов |
| `ui/components` | Карточки, чипы категорий, кольца, график, лента разделов, переключатели |

## Сборка

JDK — из Android Studio:

    JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug testDebugUnitTest

Свойство `-Pdemo` собирает демо-режим, даже если `google-services.json` на месте, — удобно проверять
интерфейс без входа в аккаунт.

compileSdk 36. Compose 1.12 и Lifecycle 2.11 уже требуют compileSdk 37: чтобы обновиться,
сначала поставить SDK Platform 37 через SDK Manager.

## Подключение Firebase

1. [Консоль Firebase](https://console.firebase.google.com) → *Create a project*, Google Analytics не нужен.
   ID проекта потом не меняется — лучше без «copilot».
2. *Build → Authentication → Get started → Sign-in method*: включить **Email/Password** и **Google**.
3. *Build → Firestore Database → Create database*: регион в ЕС (`eur3`), *production mode*.
   Во вкладке *Rules* вставить содержимое [`firestore.rules`](firestore.rules) и нажать *Publish*.
4. *Project settings → Your apps → Android*: пакет `com.artemkhateev.finance` и SHA-1 отладочного
   ключа из `./gradlew signingReport` (вариант `debug`). Для релизной сборки позже добавить SHA-1 релизного ключа.
   Без SHA-1 вход через Google срывается сразу после выбора аккаунта.
5. Скачать `google-services.json` **после** шагов 2 и 4 — только тогда в нём есть OAuth-клиент для входа
   через Google — и положить в `app/`. В git файл не попадает.

Данные пользователя лежат в `users/{uid}/accounts|categories|transactions|recurrings`. Суммы — целые центы,
даты — строки `yyyy-MM-dd`.

## Перед публикацией в стор

- Никакого «Copilot» в названии, иконке, описании и скриншотах стора: это бренд Copilot Money, Inc.,
  а слово COPILOT в США зарегистрировано ещё и за Microsoft. Google Play снимает приложения,
  которые выдают себя за чужой продукт (политика Impersonation).
- Своя иконка, палитра (`ui/theme/Color.kt`), иллюстрации и тексты; ассеты и скриншоты
  референса не копировать.
- `applicationId` (`com.artemkhateev.finance`) после первой загрузки в Play не меняется —
  выбрать до публикации.
- Имя проверить по базам товарных знаков (EUIPO / TMview, USPTO) и поиском в Google Play.
