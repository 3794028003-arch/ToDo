# Local-First Task Board

一个仅包含 `TODO`、`DOING`、`DONE` 三种状态的 Android Local-First 任务看板。

当前仓库已提供可使用的 Compose 看板：用户可以离线新建、修改、删除任务，并在 `TODO`、`DOING`、`DONE` 之间移动。所有操作先事务写入 Room 与持久化 Outbox，再由 WorkManager 在网络可用时后台同步。

## 运行 Android App

使用 Android Studio 打开 `android` 目录，选择 `app` 配置后运行；也可以连接模拟器或真机后执行：

```shell
cd android
./gradlew :app:installDebug
```

Debug 版本默认在 Android 模拟器中通过 `http://10.0.2.2:8080/` 访问本机后端。后端未启动或设备断网时，核心任务操作仍可正常使用。

需要让真机与模拟器同时连接同一个本机后端时，在不会被 Git 跟踪的 `android/local.properties` 中增加以下配置（保留文件中已有的 `sdk.dir`，并将示例 IPv4 替换为电脑当前的局域网 IPv4）：

```properties
SYNC_BASE_URL=http://<电脑IPv4>:8080/
```

也可以只对单次 Gradle 命令传入属性；该方式的优先级高于 `local.properties`：

```shell
./gradlew :app:installDebug -PSYNC_BASE_URL=http://<电脑IPv4>:8080/
```

未配置 `SYNC_BASE_URL` 时仍使用模拟器默认地址 `http://10.0.2.2:8080/`。真机必须能够访问电脑的局域网 IPv4，电脑防火墙需允许 `8080` 端口，并且两台设备应处于可互通的网络中。`local.properties` 已列入 `.gitignore`，不要将个人局域网 IP 写入受 Git 跟踪的 Gradle 文件。

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

## 使用 GitHub 发布镜像部署

### 环境要求

目标电脑需要安装并启动 Docker Desktop。

### 下载项目

```powershell
git clone https://github.com/3794028003-arch/ToDo.git
cd ToDo

```

### 创建本机配置

```powershell
Copy-Item .env.example .env
notepad .env
```

必须将 `POSTGRES_PASSWORD` 改成目标电脑自己的密码。`.env` 已被 Git 忽略，不会提交到仓库。

### 启动 Backend 和 PostgreSQL

```powershell
docker compose -f docker-compose.release.yml pull
docker compose -f docker-compose.release.yml up -d
docker compose -f docker-compose.release.yml ps
```

两个服务都显示 `healthy` 后，验证 Backend：

```powershell
Invoke-WebRequest http://localhost:8080/actuator/health
```

预期返回 HTTP 200。

### 真机连接

手机与电脑必须处于同一局域网。Android 构建配置应使用：

```properties
SYNC_BASE_URL=http://<电脑IPv4>:8080/
```

例如电脑 IPv4 为 `192.168.1.100`：

```properties
SYNC_BASE_URL=http://192.168.1.100:8080/
```

不要将个人 IP 提交到 Git。当前 APK 的 Backend 地址在构建时确定，因此换到另一台电脑后，需要使用该电脑的 IPv4 重新构建 APK。

### 停止服务

```powershell
docker compose -f docker-compose.release.yml down
```

该命令保留 PostgreSQL 数据卷。不要添加 `-v`，除非确定需要删除全部数据库数据。