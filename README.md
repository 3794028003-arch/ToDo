# Local-First Task Board

一个仅包含 `TODO`、`DOING`、`DONE` 三种状态的 Android Local-First 任务看板。

当前仓库已经完成数据层、持久化 Outbox、同步引擎、WorkManager、后端同步 API、灾难场景测试、ViewModel，以及可安装和启动的 Android 应用壳。Compose 看板 UI 尚未开始；只有在完整 CI 通过后才会进入 UI 阶段。

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
