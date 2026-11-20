# Personal Kanban — Архитектура и проектирование

## 1. Общая архитектура
Приложение построено по принципам **Clean Architecture + MVVM + Repository Pattern** с использованием **Hilt** для внедрения зависимостей и **Room** как единого источника истины (**Offline-First**).

### Слои приложения
1. **Presentation (`ui/`)**:
   - Jetpack Compose + Material Design 3.
   - Экраны:
     - `MainScreen`: верхняя панель (название, дата, статистика, статус синхронизации, настройки), строка поиска, переключатели представлений, фильтры/сортировка, вертикальная Kanban-лента со сворачиваемыми статус-группами, мультивыбор для массовых операций, нижняя панель быстрого ввода + FAB, BottomSheet создания задачи.
     - `TaskDetailScreen`: полное редактирование всех полей, выбор заказчика с автодополнением, выбор даты/времени, напоминания, метки, дублирование, архивация, удаление в корзину, история комментариев (добавление, редактирование, удаление).
     - `SettingsScreen`: тема (Светлая / Тёмная / Системная), компактность карточек, уведомления и интервалы напоминаний (включая пользовательские), Google Drive (подключение OAuth 2.0, ручная синхронизация, восстановление, история версий бэкапов, разрешение конфликтов), проверка обновлений GitHub Releases, экспорт/импорт (JSON/CSV), архив и корзина.
     - `ArchiveTrashScreen`: отдельные вкладки для Архива и Корзины с восстановлением и окончательным удалением.
     - `CustomersScreen`: справочник заказчиков с количеством задач и быстрым фильтром.
     - `StatsScreen`: аналитика выполнения и просрочек.
2. **Domain (`domain/`)**:
   - Модели предметной области (`Task`, `Comment`, `Customer`, `SyncConflict`, `BackupSnapshot`, `AppSettings`, `ReleaseInfo`).
   - Перечисления: `TaskStatus` (`INBOX`, `PLANNED`, `IN_PROGRESS`, `WAITING`, `DONE`), `TaskPriority` (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`), `SyncStatus`.
   - Логика фильтрации, сортировки, расчёта интервалов напоминаний и разрешения конфликтов синхронизации (`SyncMergeEngine`).
3. **Data (`data/`)**:
   - **Local (`data/local/`)**: Room Database (`PersonalKanbanDatabase`), DAO (`TaskDao`, `CommentDao`, `CustomerDao`, `SyncConflictDao`), DataStore (`SettingsDataStore`).
   - **Remote (`data/remote/`)**:
     - `GoogleDriveService`: работа с Google Drive REST API v3 (OAuth 2.0 токен через Google Sign-In / GoogleAuthUtil, создание папки `PersonalKanban`, загрузка/скачивание `backup_v1.json` и исторических снапшотов).
     - `GitHubUpdateService`: проверка релизов через GitHub Releases API, скачивание APK с прогрессом и проверкой SHA-256.
   - **Repository (`data/repository/`)**: `TaskRepository`, `SyncRepository`, `UpdateRepository`.
   - **Workers (`worker/`)**: `DriveSyncWorker`, `ReminderWorker`, `UpdateCheckWorker`.
   - **Widget (`widget/`)**: `KanbanWidgetProvider` (RemoteViews-виджет для главного экрана Galaxy S26 Ultra с отображением задач на сегодня/в работе и кнопкой быстрого добавления).

## 2. Схема базы данных Room
- `tasks`: `id (PK, UUID)`, `title`, `description`, `status`, `priority`, `dueAt`, `hasTime`, `customerId`, `customerName`, `labelsJson`, `reminderEnabled`, `createdAt`, `updatedAt`, `completedAt`, `lastSyncedAt`, `version`, `isArchived`, `isDeleted`, `deletedAt`.
- `comments`: `id (PK, UUID)`, `taskId (FK)`, `text`, `createdAt`, `updatedAt`, `lastSyncedAt`, `version`, `isDeleted`.
- `customers`: `id (PK, UUID)`, `name`, `createdAt`, `updatedAt`, `isDeleted`.
- `sync_conflicts`: `id (PK, UUID)`, `taskId`, `localJson`, `remoteJson`, `conflictFields`, `detectedAt`, `resolved`.

## 3. Алгоритм двусторонней синхронизации и разрешения конфликтов
1. **Tombstones**: удаление задачи или комментария устанавливает `isDeleted = true` и обновляет `updatedAt` и `version`. Записи не удаляются физически до синхронизации (или очистки корзины с сохранением tombstone).
2. **Слияние (`SyncMergeEngine`)**:
   - Если запись есть только локально и `lastSyncedAt == 0` -> добавляем в облако.
   - Если запись есть только в облаке -> сохраняем локально (если не была удалена локально с более новым tombstone).
   - Если запись изменена только с одной стороны -> побеждает изменённая сторона.
   - Если изменена с обеих сторон:
     - Независимые поля (например, локально изменили `status`, а в облаке `dueAt`) объединяются автоматически!
     - Если одно и то же поле изменено по-разному на обеих сторонах -> создаётся `SyncConflict`, локальная версия сохраняется как текущая, а пользователь получает уведомление/диалог в приложении для выбора локальной, облачной или объединённой версии.
3. **Защита от перезаписи**: перед загрузкой бэкапа проверяется SHA-256 и схема данных; при восстановлении старой версии поверх более новых локальных изменений выводится подтверждение.
