# Kibana DevOps 自动化工具

一个用于自动化 Kibana 配置和管理的 Java 工具，支持批量创建空间、数据视图、角色和用户。

## 功能特性

- 🏗️ **自动创建 Kibana 空间** - 基于项目和环境自动创建独立的工作空间
- 📊 **智能索引匹配** - 根据配置的模式自动匹配和添加数据视图
- 👤 **用户角色管理** - 自动创建项目相关的角色和用户
- ⚙️ **YAML 配置驱动** - 通过简单的 YAML 文件配置所有参数
- 🔍 **详细日志输出** - 提供清晰的操作进度和结果反馈

## 快速开始

### 环境要求

- Java 11 或更高版本
- Gradle 7.0 或更高版本

### 安装和运行

1. 克隆项目
```bash
git clone <repository-url>
cd kibana-devops-tool
```

2. 配置 Kibana 连接信息
编辑 `src/main/resources/kibana-config.yml` 文件：

```yaml
kibana:
  baseUrl: "https://your-kibana-url.com"
  username: "your-username"
  password: "your-password"

project:
  name: "your-project-name"
  environment: "prod"  # 或 dev, test 等

settings:
  autoCreateSpace: true
  autoAddIndices: true
  autoCreateRole: true
  autoCreateUser: true
  showDetailedMatching: true
```

3. 运行应用
```bash
./gradlew run
```

或者构建并运行 JAR：
```bash
./gradlew build
java -jar build/libs/kibana-devops-tool-1.0-SNAPSHOT.jar
```

## 配置说明

### 基本配置

- `kibana.baseUrl`: Kibana 服务器地址
- `kibana.username/password`: 认证凭据
- `project.name`: 项目名称
- `project.environment`: 环境标识

### 自动化设置

- `autoCreateSpace`: 自动创建 Kibana 空间
- `autoAddIndices`: 自动添加匹配的数据视图
- `autoCreateRole`: 自动创建项目角色
- `autoCreateUser`: 自动创建项目用户
- `showDetailedMatching`: 显示详细的索引匹配信息

## 项目结构

```
src/
├── main/
│   ├── java/io/github/devops/
│   │   ├── Main.java           # 主程序入口
│   │   ├── KibanaConfig.java   # 配置文件解析
│   │   └── KibanaUtils.java    # Kibana 操作工具类
│   └── resources/
│       └── kibana-config.yml   # 配置文件
```

## 开发

使用 Gradle 进行构建和依赖管理：

```bash
# 编译项目
./gradlew build

# 运行测试
./gradlew test

# 运行应用
./gradlew run
```

## 依赖

- Jackson (JSON/YAML 处理)
- JUnit 5 (测试框架)

## 许可证

本项目采用开源许可证，具体请查看 LICENSE 文件。