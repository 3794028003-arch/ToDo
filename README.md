# Local-First Task Board

一个仅包含 `TODO`、`DOING`、`DONE` 三种状态的 Android Local-First 任务看板。

当前仓库已提供可使用的 Compose 看板：用户可以离线新建、修改、删除任务，并在 `TODO`、`DOING`、`DONE` 之间移动。所有操作先事务写入 Room 与持久化 Outbox，再由 WorkManager 在网络可用时后台同步。

## 运行 Android App

使用 Android Studio 打开 `android` 目录，选择 `app` 配置后运行；也可以连接模拟器或真机后执行：

```shell
cd android
./gradlew :app:installDebug
```

Debug 版本在 Android 模拟器中通过 `http://10.0.2.2:8080/` 访问本机后端。后端未启动或设备断网时，核心任务操作仍可正常使用。

## 启动后端

本机只需安装 Docker，无需单独安装 PostgreSQL：

```shell
docker compose up -d
```

默认 API 地址为 `http://localhost:8080`，健康检查地址为 `http://localhost:8080/actuator/health`。

如需修改本地数据库配置或端口，复制 `.env.example` 为 `.env` 后调整；`.env` 不会被提交到 Git。

## 架构边界

```text
UI -> ViewModel -> Repository -> Room / Sync Engine -> Backend API
```

Room 是 Android 任务数据的唯一可信来源。用户操作先以事务方式写入 Room 与持久化 Outbox，UI 不等待网络；WorkManager 在后台恢复并重试同步。

## 本地验证

Android Lint 与 JVM 测试：

```shell
cd android
./gradlew lint test
```

连接模拟器或真机后，验证应用启动并安装 Debug APK：

```shell
cd android
./gradlew :app:connectedDebugAndroidTest :app:installDebug
```

后端静态分析、测试与构建：

```shell
cd backend
./gradlew detekt test build
```

GitHub Actions 在每次 `push` 和 `pull_request` 时执行相同的质量门禁。
