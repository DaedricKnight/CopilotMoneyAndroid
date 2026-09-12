# CopilotMoneyAndroid

Рабочее название. Неофициальный проект, не связан с Copilot Money, Inc.
Перед публикацией сменить название и айдентику — см. «Перед публикацией».

Приложение для личных финансов под Android: бюджеты, транзакции, регулярные платежи,
денежный поток. Референс по UX и визуальному языку — Copilot Money (iOS).

## Что уже сделано

- Каркас: шапка, лента разделов с выбранным пунктом по центру, свайп между разделами.
- **Dashboard**: остаток бюджета и линия расходов против равномерного темпа, блок «To review»
  с отметкой просмотренного, кольца бюджетов по категориям.
- **Transactions**: лента по дням — категория, счёт, сумма, отметка непросмотренных.
- Остальные разделы (Cash flow, Accounts, Investments, Categories, Goals, Recurrings) — заглушки.
- Данные — демо в памяти за интерфейсом `FinanceRepository`; облачная реализация встанет на его место.

## Устройство

| Путь | Что там |
| --- | --- |
| `data/model` | Модели. Деньги — `Money` в центах, не `Double` |
| `data/demo` | Демо-данные за прошлый и текущий месяц относительно сегодняшней даты |
| `feature/*` | Экраны. Состояние считается чистыми функциями (`buildDashboard`) и покрыто тестами |
| `ui/theme` | Все цвета, шрифты и формы. Смена айдентики — правка этих файлов |
| `ui/components` | Карточки, чипы категорий, кольца, график, лента разделов |

## Сборка

JDK — из Android Studio:

    JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug testDebugUnitTest

compileSdk 36. Compose 1.12 и Lifecycle 2.11 уже требуют compileSdk 37: чтобы обновиться,
сначала поставить SDK Platform 37 через SDK Manager.

## Облако (план)

Firebase — часть Google Cloud: Firestore как база, Firebase Authentication для входа.

- Данные пользователя: `users/{uid}/accounts|categories|transactions|recurrings/{id}`, суммы — целые центы.
- Security Rules: каждый читает и пишет только свой `users/{uid}`.
- Регион базы — в ЕС (например, `eur3`): после создания его не поменять.
- `app/google-services.json` в git не кладём.

## Перед публикацией

- Никакого «Copilot» в названии, иконке, описании и скриншотах стора: это бренд Copilot Money, Inc.,
  а слово COPILOT в США зарегистрировано ещё и за Microsoft. Google Play снимает приложения,
  которые выдают себя за чужой продукт (политика Impersonation).
- Своя иконка, палитра (`ui/theme/Color.kt`), иллюстрации и тексты; ассеты и скриншоты
  референса не копировать.
- `applicationId` (`com.artemkhateev.finance`) после первой загрузки в Play не меняется —
  выбрать до публикации.
- Имя проверить по базам товарных знаков (EUIPO / TMview, USPTO) и поиском в Google Play.
