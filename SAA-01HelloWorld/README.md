# 🏥 SAA-01HelloWorld - 中老年人智能医疗健康助手

> 基于 Spring AI Alibaba + LangChain4j + Milvus + MCP 的智能医疗健康问答系统

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-0.36.2-blue.svg)](https://github.com/langchain4j/langchain4j)
[![Milvus](https://img.shields.io/badge/Milvus-2.6.15-red.svg)](https://milvus.io/)
[![MCP](https://img.shields.io/badge/MCP-1.0-purple.svg)](https://modelcontextprotocol.io/)

---

## 📖 项目简介

本项目是一个面向中老年人的智能医疗健康助手系统，名为 **"嘎嘎"**。系统集成了多种 AI 能力，能够为用户提供健康咨询、疾病预防建议、用药指导等服务，并具备紧急情况识别和安全内容过滤机制。

### ✨ 核心特性

- 🤖 **智能对话** - 基于智谱 GLM-4 大模型的自然语言交互
- 📚 **知识库增强** - 支持 RAG（检索增强生成），结合向量数据库实现知识检索
- 🛡️ **安全机制** - 紧急情况检测、内容过滤、免责声明
- 💾 **会话管理** - 多会话独立记忆，上下文感知对话
- 🔧 **工具调用** - 症状评估、科室推荐等医疗专业工具
- 🔌 **MCP 协议** - 支持 Model Context Protocol，连接 Memory/Sequential Thinking/Time 服务
- 🌊 **SSE 流式输出** - 实时流式响应，提升用户体验
- 🔐 **用户认证** - JWT Token 认证，支持注册/登录
- 💊 **用药提醒** - 定时用药提醒计划管理，服药记录追踪

---

## 🏗️ 项目架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            客户端层                                       │
│                    (Web Frontend / API Client / SSE)                     │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Controller 层                                   │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────────────┐  │
│  │ChatController│ │AgentController│ │MedicalStream│ │StreamChatCtrl │  │
│  │   /chat      │ │   /agent      │ │/api/stream  │ │  /api/stream   │  │
│  └──────────────┘ └──────────────┘ └──────────────┘ └────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Service 层                                      │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────────────┐  │
│  │KnowledgeBase │ │MilvusKnowledge│ │PdfParseSvc  │ │ McpToolService │  │
│  │   Service    │ │    Service   │ │              │ │  (MCP Client)  │  │
│  └──────────────┘ └──────────────┘ └──────────────┘ └────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
┌───────────────────────┐ ┌─────────────────┐ ┌─────────────────────────────┐
│   AI 集成层           │ │   MCP 服务层     │ │        存储层               │
│   (LangChain4j)       │ │ (Model Context  │ │                             │
│ ┌─────────┐ ┌───────┐ │ │   Protocol)     │ │ ┌─────────┐ ┌────────────┐ │
│ │ChatModel│ │Embed  │ │ │ ┌─────────────┐ │ │ │ Milvus  │ │ InMemory   │ │
│ │(Stream) │ │Model  │ │ │ │Memory MCP   │ │ │ │向量库   │ │向量库      │ │
│ └─────────┘ └───────┘ │ │ │(知识图谱)   │ │ │ └─────────┘ └────────────┘ │
│ ┌─────────┐           │ │ └─────────────┘ │ │                             │
│ │AiService│           │ │ ┌─────────────┐ │ │ ┌─────────────────────────┐│
│ │(Agent)  │           │ │ │Sequential   │ │ │ │ 本地文件存储(持久化)   ││
│ └─────────┘           │ │ │Thinking MCP │ │ │ └─────────────────────────┘│
│                       │ │ └─────────────┘ │ └─────────────────────────────┘
│                       │ │ ┌─────────────┐ │
│                       │ │ │Time MCP     │ │
│                       │ │ │(时间服务)   │ │
│                       │ │ └─────────────┘ │
└───────────────────────┘ └─────────────────┘
```

---

## 📁 项目结构

```
SAA-01HelloWorld/
├── src/main/java/com/lww/
│   ├── Saa01HelloWordApplication.java    # 启动类
│   │
│   ├── config/                           # 配置类
│   │   ├── SecurityConfig.java           # Spring Security 安全配置
│   │   ├── JwtUtils.java                 # JWT 工具类
│   │   ├── JwtAuthenticationFilter.java  # JWT 认证过滤器
│   │   ├── MilvusConfig.java             # Milvus 向量数据库配置
│   │   └── MedicalToolsConfig.java       # 医疗工具配置
│   │
│   ├── configer/                         # LangChain4j 配置
│   │   ├── LangChina4JConfig.java        # AI 模型、工具、Agent 配置
│   │   └── SimpleLock.java               # 简易锁实现
│   │
│   ├── controller/                       # 控制器层
│   │   ├── ChatController.java           # 基础聊天接口
│   │   ├── ChatRequest.java              # 请求 DTO
│   │   ├── AgentController.java          # Agent 智能体接口
│   │   ├── RagController.java            # RAG 知识库管理接口
│   │   ├── StreamChatController.java     # SSE 流式聊天接口
│   │   ├── StreamRagController.java      # SSE 流式 RAG 接口
│   │   └── MedicalStreamController.java  # 医疗流式聊天接口
│   │
│   ├── mcp/                              # MCP 协议模块
│   │   ├── McpClientConfig.java          # MCP Client 配置
│   │   ├── McpToolService.java           # MCP 工具服务封装
│   │   └── MedicalMcpToolRegistrar.java  # MCP Server 工具注册
│   │
│   ├── kb/                               # 知识库服务
│   │   └── KnowledgeBaseService.java     # 知识库 CRUD + 向量检索
│   │
│   ├── medical/                          # 医疗模块
│   │   ├── MedicalChatController.java    # 医疗对话控制器
│   │   ├── SafetyGuard.java              # 安全机制（紧急检测/内容过滤）
│   │   │
│   │   ├── context/                      # 上下文管理
│   │   │   ├── HybridContext.java        # 混合上下文
│   │   │   └── HybridContextManager.java # 上下文管理器
│   │   │
│   │   ├── dto/                          # 数据传输对象
│   │   │   ├── ChatRequest.java          # 聊天请求
│   │   │   └── ChatResponse.java         # 聊天响应
│   │   │
│   │   ├── session/                      # 会话管理
│   │   │   ├── Session.java              # 会话实体
│   │   │   ├── SessionStatus.java        # 会话状态
│   │   │   └── SessionManager.java       # 会话管理器
│   │   │
│   │   └── tools/                        # 医疗工具
│   │       └── MedicalTools.java         # 症状评估、科室推荐
│   │
│   ├── user/                             # 用户模块 ⭐新增
│   │   ├── controller/
│   │   │   └── AuthController.java       # 认证控制器（注册/登录）
│   │   │
│   │   ├── entity/
│   │   │   └── User.java                 # 用户实体
│   │   │
│   │   ├── repository/
│   │   │   └── UserRepository.java       # 用户数据访问层
│   │   │
│   │   ├── service/
│   │   │   ├── UserService.java          # 用户业务服务
│   │   │   └── UserDetailsServiceImpl.java # Spring Security 用户服务
│   │   │
│   │   └── dto/
│   │       ├── LoginRequest.java         # 登录请求
│   │       ├── RegisterRequest.java      # 注册请求
│   │       └── UserResponse.java         # 用户响应
│   │
│   ├── medication/                       # 用药提醒模块 ⭐新增
│   │   ├── controller/
│   │   │   └── MedicationController.java # 用药管理控制器
│   │   │
│   │   ├── entity/
│   │   │   ├── MedicationReminder.java   # 用药提醒实体
│   │   │   └── MedicationRecord.java     # 服药记录实体
│   │   │
│   │   ├── repository/
│   │   │   ├── MedicationReminderRepository.java # 提醒数据访问
│   │   │   └── MedicationRecordRepository.java   # 记录数据访问
│   │   │
│   │   ├── service/
│   │   │   ├── MedicationReminderService.java # 提醒服务
│   │   │   ├── MedicationRecordService.java   # 记录服务
│   │   │   └── ReminderScheduler.java         # 定时任务调度器
│   │   │
│   │   └── dto/
│   │       ├── ReminderRequest.java      # 提醒请求
│   │       ├── ReminderResponse.java     # 提醒响应
│   │       └── RecordResponse.java       # 记录响应
│   │
│   └── service/                          # 服务层
│       ├── RagService.java               # RAG 检索增强生成服务
│       ├── MilvusEmbeddingStore.java     # Milvus Embedding 存储实现
│       ├── MilvusKnowledgeService.java   # Milvus 向量检索服务
│       └── PdfParseService.java          # PDF 文档解析服务
│
├── src/main/resources/
│   ├── application.yml                   # 应用配置
│   ├── static/                           # 静态资源
│   │   ├── index.html                    # 主页（医疗资讯大屏）
│   │   ├── login.html                    # 登录/注册页面
│   │   ├── medication-calendar.html      # 用药日历页面
│   │   ├── digital-human.html            # 数字人界面
│   │   └── img.png                       # 嘎嘎助手头像
│   └── configer/
│       └── banner.txt                    # 启动 Banner
│
├── data/kb/                              # 知识库数据目录
│   ├── kb-items.json                     # 知识条目
│   └── kb-embeddings.json                # 向量嵌入缓存
│
└── pom.xml                               # Maven 依赖配置
```

---

## 🚀 快速开始

### 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 17+ |
| Maven | 3.8+ |
| Milvus | 2.6+ (可选) |

### 配置文件

编辑 `src/main/resources/application.yml`：

```yaml
server:
  port: 8002

spring:
  application:
    name: SAA-01HelloWord

  # MySQL 数据源配置
  datasource:
    url: jdbc:mysql://localhost:3306/first?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=utf-8
    username: root
    password: 123456
    driver-class-name: com.mysql.cj.jdbc.Driver

  # JPA 配置
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false

  ai:
    zhipuai:
      api-key: YOUR_ZHIPU_API_KEY    # 替换为你的智谱 API Key
      chat:
        options:
          model: glm-4-flash

  # MCP 配置
  ai:
    mcp:
      server:
        name: medical-mcp-server
        version: 1.0.0
        type: sync
        sse-endpoint: /sse
        sse-message-endpoint: /mcp/message
      client:
        memory:
          enabled: true
        sequential-thinking:
          enabled: true
        time:
          enabled: true

# JWT 配置
jwt:
  secret: your-jwt-secret-key-at-least-256-bits-long-for-security
  expiration: 86400000  # 24小时（毫秒）

# Milvus 向量数据库配置
milvus:
  endpoint: 127.0.0.1:19530
  database: default
  collection: latitude15
```

### 启动项目

```bash
# 1. 克隆项目
cd D:\LWW\AsolfOpen\SpringAiAlibaba-v1\SAA-01HelloWorld

# 2. 编译项目
mvn clean install -DskipTests

# 3. 启动服务
mvn spring-boot:run
```

服务启动后访问: http://localhost:8002

---

## 📡 API 接口

### 1. 用户认证 ⭐新增

#### 用户注册
```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "testuser",
  "password": "123456",
  "confirmPassword": "123456",
  "nickname": "测试用户"
}
```

**响应示例：**
```json
{
  "id": 1,
  "username": "testuser",
  "nickname": "测试用户",
  "createdAt": "2024-04-07T12:00:00"
}
```

#### 用户登录
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "testuser",
  "password": "123456"
}
```

**响应示例：**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "type": "Bearer"
}
```

#### 获取当前用户信息
```http
GET /api/auth/me
Authorization: Bearer {token}
```

#### 退出登录
```http
POST /api/auth/logout
Authorization: Bearer {token}
```

### 2. 用药提醒管理 ⭐新增

#### 获取提醒计划列表
```http
GET /api/medication/reminders
Authorization: Bearer {token}
```

#### 创建提醒计划
```http
POST /api/medication/reminders
Authorization: Bearer {token}
Content-Type: application/json

{
  "medicationName": "阿司匹林",
  "dosage": "1片/次",
  "frequency": "DAILY",
  "remindTimes": ["08:00", "20:00"],
  "daysOfWeek": "1,2,3,4,5,6,7",
  "startDate": "2024-04-07",
  "endDate": "2024-05-07",
  "notes": "饭后服用"
}
```

**频率类型：**
| 值 | 说明 |
|----|------|
| `DAILY` | 每天 |
| `WEEKLY` | 每周（需配合 daysOfWeek） |

#### 更新提醒计划
```http
PUT /api/medication/reminders/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "medicationName": "阿司匹林",
  "dosage": "2片/次",
  "frequency": "DAILY",
  "remindTimes": ["08:00", "12:00", "20:00"]
}
```

#### 删除提醒计划
```http
DELETE /api/medication/reminders/{id}
Authorization: Bearer {token}
```

#### 启用/禁用提醒
```http
PATCH /api/medication/reminders/{id}/toggle?enabled=true
Authorization: Bearer {token}
```

#### 获取待服药记录（轮询接口）
```http
GET /api/medication/pending
Authorization: Bearer {token}
```

#### 确认服药
```http
POST /api/medication/records/{id}/confirm
Authorization: Bearer {token}
```

#### 跳过服药
```http
POST /api/medication/records/{id}/skip?reason=外出
Authorization: Bearer {token}
```

#### 获取日历数据
```http
GET /api/medication/calendar?start=2024-04-01T00:00:00&end=2024-04-30T23:59:59
Authorization: Bearer {token}
```

#### 获取服药统计
```http
GET /api/medication/stats?days=7
Authorization: Bearer {token}
```

**响应示例：**
```json
{
  "totalRecords": 14,
  "completedCount": 12,
  "missedCount": 2,
  "complianceRate": 85.7
}
```

### 3. 基础聊天

```http
POST /chat
Content-Type: application/json

{
  "input": "你好，请介绍一下你自己"
}
```

### 2. Agent 智能体

```http
POST /agent
Content-Type: application/json

{
  "input": "帮我查询一下高血压的相关知识"
}
```

### 3. 医疗健康对话

```http
POST /api/medical/chat
Content-Type: application/json

{
  "sessionId": "可选-会话ID",
  "patientId": "可选-患者ID",
  "message": "最近总是头晕，应该怎么办？"
}
```

**响应示例：**

```json
{
  "sessionId": "sess_abc123",
  "reply": "您好！头晕可能由多种原因引起，建议您先测量一下血压...",
  "emergency": false
}
```

### 4. SSE 流式聊天 ⭐新增

```http
GET /api/stream/chat?message=你好
Accept: text/event-stream
```

**POST 方式（支持更长消息）：**

```http
POST /api/stream/chat
Content-Type: application/json
Accept: text/event-stream

{
  "input": "请详细介绍一下高血压的预防和治疗方法"
}
```

**SSE 事件类型：**

| 事件名 | 说明 |
|--------|------|
| `message` | AI 回复片段（逐字输出） |
| `done` | 流式输出完成 |
| `error` | 错误信息 |

**前端接收示例：**

```javascript
const eventSource = new EventSource('/api/stream/chat?message=你好');
eventSource.addEventListener('message', (event) => {
    console.log('收到片段:', event.data);
});
eventSource.addEventListener('done', () => {
    console.log('输出完成');
    eventSource.close();
});
```

### 5. 医疗流式对话 ⭐新增

```http
POST /api/stream/medical/chat
Content-Type: application/json
Accept: text/event-stream

{
  "sessionId": "可选-会话ID",
  "message": "最近总是头晕，应该怎么办？"
}
```

**SSE 事件类型：**

| 事件名 | 说明 |
|--------|------|
| `emergency` | 紧急情况警报（优先发送） |
| `message` | AI 回复片段 |
| `emergency_append` | 紧急提醒追加内容 |
| `done` | 输出完成 |
| `error` | 错误信息 |

### 6. 清除会话

```http
DELETE /api/medical/session/{sessionId}
DELETE /api/stream/medical/session/{sessionId}
```

### 7. RAG 知识库管理

#### 添加单条文档
```http
POST /api/kb/add
Content-Type: application/json

{
  "content": "高血压患者应每日定时测量血压，保持低盐饮食。",
  "source": "健康指南"
}
```

#### 批量添加文档
```http
POST /api/kb/batch
Content-Type: application/json

[
  {"content": "文档内容1", "source": "来源1"},
  {"content": "文档内容2", "source": "来源2"}
]
```

#### 搜索知识库
```http
GET /api/kb/search?query=高血压&topK=5&minScore=0.7
```

**响应示例：**
```json
[
  {
    "id": "1234567890",
    "score": 0.85,
    "content": "高血压患者应每日定时测量血压...",
    "source": "健康指南"
  }
]
```

#### 获取 RAG 上下文
```http
GET /api/kb/context?query=高血压注意事项&topK=3
```

#### 获取知识库统计
```http
GET /api/kb/stats
```

#### 删除文档
```http
DELETE /api/kb/{id}
```

---

## 🛠️ 核心模块说明

### 🔐 用户认证模块 ⭐新增

基于 JWT Token 的用户认证系统：

| 功能 | 说明 |
|------|------|
| 用户注册 | 用户名唯一性校验，密码 BCrypt 加密 |
| 用户登录 | 返回 JWT Token，有效期 24 小时 |
| Token 验证 | 请求头携带 `Authorization: Bearer {token}` |
| 权限控制 | 基于 Spring Security 的接口权限管理 |

**安全配置：**
- 密码加密：BCrypt
- Token 有效期：24 小时
- 无状态会话：SessionCreationPolicy.STATELESS

### 💊 用药提醒模块 ⭐新增

完整的用药提醒与服药记录管理系统：

#### 功能特性

| 功能 | 说明 |
|------|------|
| 提醒计划管理 | 创建、编辑、删除、启用/禁用提醒 |
| 多时间点提醒 | 支持每天多个服药时间点 |
| 频率设置 | 支持每天、每周指定日期 |
| 定时生成记录 | 每分钟扫描，自动生成服药记录 |
| 服药状态追踪 | 待服、已服、跳过、漏服 |
| 依从性统计 | 计算服药依从性百分比 |

#### 定时任务 (ReminderScheduler)

```java
@Scheduled(cron = "0 * * * * ?")  // 每分钟执行
public void generateMedicationRecords() {
    // 扫描所有启用的提醒
    // 匹配当前时间窗口（±1分钟）
    // 生成服药记录
    // 标记漏服记录
}
```

#### API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/medication/reminders` | GET | 获取提醒列表 |
| `/api/medication/reminders` | POST | 创建提醒 |
| `/api/medication/reminders/{id}` | PUT | 更新提醒 |
| `/api/medication/reminders/{id}` | DELETE | 删除提醒 |
| `/api/medication/reminders/{id}/toggle` | PATCH | 启用/禁用 |
| `/api/medication/pending` | GET | 获取待服药记录 |
| `/api/medication/records/{id}/confirm` | POST | 确认服药 |
| `/api/medication/records/{id}/skip` | POST | 跳过服药 |
| `/api/medication/calendar` | GET | 获取日历数据 |
| `/api/medication/stats` | GET | 获取统计数据 |

### 🔒 安全机制 (SafetyGuard)

| 功能 | 说明 |
|------|------|
| 紧急检测 | 识别"胸痛"、"呼吸困难"等紧急关键词 |
| 内容过滤 | 自动替换确诊性表述，避免误诊 |
| 免责声明 | 自动附加医疗免责声明 |

### 📚 知识库服务 (KnowledgeBaseService)

支持以下操作：

| 方法 | 功能 |
|------|------|
| `put(id, title, content)` | 写入知识条目 |
| `get(id)` | 按 ID 读取知识 |
| `search(query, topK)` | 语义相似度搜索 |

### 📖 RAG 服务 (RagService)

基于 Milvus + ZhipuAI Embedding 的知识库服务：

| 功能 | 说明 |
|------|------|
| 向量存储 | Milvus + COSINE 相似度度量 |
| Embedding | ZhipuAI Embedding (1024维) |
| 检索阈值 | 默认 minScore=0.7 |
| 上下文构建 | 自动组装检索结果为 Prompt 上下文 |

| 方法 | 功能 |
|------|------|
| `addDocument(content, source)` | 添加单条文档 |
| `addDocuments(List)` | 批量添加文档 |
| `search(query, topK, minScore)` | 语义检索 |
| `buildContext(query, topK)` | 构建 RAG 上下文 |
| `getDocumentCount()` | 获取文档数量 |
| `deleteDocument(id)` | 删除文档 |

### 🔌 MCP 协议模块 ⭐新增

基于 Model Context Protocol 的外部服务集成，提供三种 MCP 服务：

#### 1. Memory MCP（知识图谱记忆）

| 方法 | 功能 |
|------|------|
| `storeMemory(sessionId, message, role)` | 存储对话记忆到知识图谱 |
| `retrieveMemory(sessionId, query)` | 检索相关历史记忆 |
| `getConversationHistory(sessionId)` | 获取完整对话历史 |

#### 2. Sequential Thinking MCP（结构化推理）

| 方法 | 功能 |
|------|------|
| `sequentialThinking(problem, context)` | 结构化思考推理 |
| `analyzeSymptomsWithReasoning(symptoms)` | 多步骤症状分析推理 |

#### 3. Time MCP（时间服务）

| 方法 | 功能 |
|------|------|
| `getCurrentTime(timezone)` | 获取当前时间（支持时区） |
| `convertTimezone(sourceTime, srcZone, targetZone)` | 时区转换 |
| `createMedicationReminder(name, time, instructions)` | 创建用药提醒 |
| `calculateNextDoseTime(lastTime, intervalHours)` | 计算下次服药时间 |

#### MCP Server 工具注册 (MedicalMcpToolRegistrar)

将医疗工具注册为 MCP Server 工具，供其他 MCP Client 调用：

| 工具 | 功能 |
|------|------|
| `assess_symptoms` | 症状评估 |
| `recommend_department` | 科室推荐 |
| `search_medical_knowledge` | 医学知识检索 |
| `deep_analyze_symptoms` | 深度症状分析 |
| `store_health_memory` | 存储健康记忆 |
| `retrieve_health_memory` | 检索健康记忆 |
| `create_medication_reminder` | 创建用药提醒 |
| `suggest_appointment_time` | 预约时间计算 |
| `create_medication_reminder_plan` | 通过自然语言创建用药提醒计划 ⭐新增 |
| `get_my_medication_reminders` | 查询用户的用药提醒列表 ⭐新增 |

### 🌊 SSE 流式输出模块 ⭐新增

基于 Server-Sent Events 的实时流式响应：

| 控制器 | 端点 | 说明 |
|--------|------|------|
| StreamChatController | `/api/stream/chat` | 基础流式聊天 |
| StreamRagController | `/api/stream/rag` | 流式 RAG 检索 |
| MedicalStreamController | `/api/stream/medical/chat` | 医疗流式对话（集成 RAG + MCP + 安全检测） |

**特性：**
- 逐字流式输出，提升用户体验
- 支持紧急情况优先推送
- 自动连接断开检测
- 60-120 秒超时保护

### 🤖 Agent 工具集

通过 LangChain4j 的 `@Tool` 注解实现：

| 工具 | 功能 |
|------|------|
| `kb_put` | 写入知识库 |
| `kb_get` | 读取知识库 |
| `kb_search` | 搜索知识库 |
| `http_get` | HTTP GET 请求 |
| `nowUtcIso` | 获取当前时间 |

---

## 🔧 技术栈

| 类别 | 技术 |
|------|------|
| **框架** | Spring Boot 3.x |
| **AI 集成** | Spring AI Alibaba, LangChain4j |
| **大模型** | 智谱 GLM-4-Flash |
| **向量数据库** | Milvus 2.6 |
| **关系数据库** | MySQL 8.0 |
| **ORM** | Spring Data JPA |
| **安全框架** | Spring Security |
| **认证方式** | JWT Token |
| **密码加密** | BCrypt |
| **Embedding** | ZhipuAI Embedding (1024维) |
| **MCP 协议** | Model Context Protocol SDK |
| **流式输出** | Server-Sent Events (SSE) |
| **定时任务** | Spring Scheduling |
| **工具库** | Lombok, Jackson |

---

## 📦 依赖版本

```xml
<langchain4j.version>0.36.2</langchain4j.version>
<milvus-sdk.version>2.6.15</milvus-sdk.version>
<spring-ai-alibaba.version>最新版</spring-ai-alibaba.version>
```

---

## 🗺️ 路线图

- [x] 基础聊天功能
- [x] LangChain4j 集成
- [x] 智谱 AI 模型对接
- [x] 医疗安全机制
- [x] 会话管理
- [x] Milvus RAG 完整集成
  - [x] 向量存储 (COSINE 相似度)
  - [x] 知识库 CRUD API
  - [x] 语义检索 (阈值 0.7)
  - [x] RAG 上下文构建
- [x] MCP 协议集成
  - [x] MCP Client (Memory/Sequential Thinking/Time)
  - [x] MCP Server 工具注册
  - [x] 医疗工具 MCP 暴露
- [x] SSE 流式输出
  - [x] 基础流式聊天
  - [x] 医疗流式对话
  - [x] 紧急事件优先推送
- [x] 用户认证系统 ⭐新增
  - [x] 用户注册/登录
  - [x] JWT Token 认证
  - [x] Spring Security 集成
  - [x] 接口权限控制
- [x] 用药提醒系统 ⭐新增
  - [x] 提醒计划管理 (CRUD)
  - [x] 定时任务调度
  - [x] 服药记录追踪
  - [x] 日历视图数据
  - [x] 服药统计与依从性
  - [x] AI 自然语言创建提醒（说"我要每天8点吃药"自动创建）⭐新增
- [x] 前端页面 ⭐新增
  - [x] 登录/注册页面
  - [x] 主页（医疗资讯大屏）
  - [x] 用药日历页面
  - [x] AI 对话集成
- [ ] PDF 文档解析入库
- [ ] 多模态支持（语音、图像）
- [ ] 用户画像与个性化推荐
- [ ] 微信/短信提醒推送

---

## 👥 贡献指南

欢迎提交 Issue 和 Pull Request！

---

## 📄 许可证

MIT License

---

<p align="center">
  <b>康养小助手嘎嘎 - 守护中老年人健康</b>
</p>
