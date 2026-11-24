# Библиотека

**Библиотечный абонемент в телефоне:** поиск книг, бронирование и учёт выдач в одном Android-приложении.

## Возможности

| Читатель | Библиотекарь |
| --- | --- |
| Ищет книги по названию и автору | Видит все активные брони и выдачи |
| Бронирует доступный экземпляр и отменяет бронь | Подтверждает выдачу и принимает возврат |
| Следит за сроками и историей | Видит данные читателя и срок возврата |

На экране входа достаточно выбрать роль: приложение откроет общую демонстрационную учётную запись читателя или библиотекаря без пароля. Для смены роли выйдите из текущей учётной записи.

## Стек

- **Android:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, ViewModel и Coroutines.
- **Сеть и данные:** Retrofit, OkHttp, Kotlin Serialization и DataStore.
- **Сервер:** Python, FastAPI, SQLAlchemy, SQLite и JWT.
- **Тестирование API:** pytest.

## Материалы

- [Исходный код Android](kotlin/) и [исходный код API](api/).
- [Видео приложения](releases/library-demo.mp4) — демонстрация работы библиотеки.
- [Скачать debug APK](releases/library-debug.apk) · для эмулятора Android; настройка сервера для телефона описана ниже.

## Запуск

### Сервер

```bash
cd api
python3 -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

При первом запуске создаются база SQLite и каталог. Документация API доступна на [localhost:8000/docs](http://localhost:8000/docs). Вход по роли предназначен для демонстрации: любой, у кого есть доступ к серверу, может открыть роль библиотекаря и общие данные читателя. Не используйте этот режим с реальными данными или на публичном сервере.

### Android

Откройте папку `kotlin` в Android Studio или соберите debug APK:

```bash
cd kotlin
./gradlew assembleDebug
```

APK появится в `kotlin/app/build/outputs/apk/debug/`. Для эмулятора сервер указан как `http://10.0.2.2:8000/`. На физическом устройстве измените адрес в [`ApiConfig.kt`](kotlin/app/src/main/java/com/example/libary/data/ApiConfig.kt) на IP компьютера в той же сети и пересоберите приложение. Сервер должен быть запущен и доступен устройству.

### Проверка API

```bash
cd api
.venv/bin/pytest -q
```
