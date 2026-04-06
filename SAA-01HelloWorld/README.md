# 🏥 SAA-01HelloWorld - 中老年人智能医疗健康助手

> 基于 Spring AI Alibaba + LangChain4j + Milvus 的智能医疗健康问答系统

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-0.36.2-blue.svg)](https://github.com/langchain4j/langchain4j)
[![Milvus](https://img.shields.io/badge/Milvus-2.6.15-red.svg)](https://milvus.io/)

---

## 📖 项目简介

本项目是一个面向中老年人的智能医疗健康助手系统，名为 **"嘎嘎"**。系统集成了多种 AI 能力，能够为用户提供健康咨询、疾病预防建议、用药指导等服务，并具备紧急情况识别和安全内容过滤机制。

### ✨ 核心特性

- 🤖 **智能对话** - 基于智谱 GLM-4 大模型的自然语言交互
- 📚 **知识库增强** - 支持 RAG（检索增强生成），结合向量数据库实现知识检索
- 🛡️ **安全机制** - 紧急情况检测、内容过滤、免责声明
- 💾 **会话管理** - 多会话独立记忆，上下文感知对话
- 🔧 **工具调用** - 症状评估、科室推荐等医疗专业工具

---

## 🏗️ 项目架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         客户端层                                  │
│                   (Web Frontend / API Client)                    │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Controller 层                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │ ChatController  │  │ AgentController │  │MedicalController│  │
│  │   /chat         │  │   /agent        │  │  /api/medical   │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Service 层                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │KnowledgeBase    │  │MilvusKnowledge  │  │ PdfParseService │  │
│  │   Service       │  │    Service      │  │                 │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                       AI 集成层 (LangChain4j)                    │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │ ChatLanguage    │  │ EmbeddingModel  │  │   AiServices    │  │
│  │    Model        │  │ (ZhipuAI GLM)   │  │   (Agent)       │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                       存储层                                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │  Milvus 向量库  │  │ InMemory 向量库 │  │  本地文件存储   │  │
│  │  (知识库RAG)    │  │   (开发测试)    │  │  (持久化)       │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📁 项目结构

```
SAA-01HelloWorld/
├── src/main/java/com/lww/
│   ├── Saa01HelloWordApplication.java    # 启动类
│   │
│   ├── config/                           # 配置类
│   │   ├── MilvusConfig.java             # Milvus 向量数据库配置
│   │   └── WebCorsConfig.java            # 跨域配置
│   │
│   ├── configer/                         # LangChain4j 配置
│   │   ├── LangChina4JConfig.java        # AI 模型、工具、Agent 配置
│   │   └── SimpleLock.java               # 简易锁实现
│   │
│   ├── controller/                       # 控制器层
│   │   ├── ChatController.java           # 基础聊天接口
│   │   ├── ChatRequest.java              # 请求 DTO
│   │   ├── AgentController.java          # Agent 智能体接口
│   │   └── RagController.java            # RAG 知识库管理接口
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
│   └── service/                          # 服务层
│       ├── RagService.java               # RAG 检索增强生成服务
│       ├── MilvusEmbeddingStore.java     # Milvus Embedding 存储实现
│       ├── MilvusKnowledgeService.java   # Milvus 向量检索服务
│       └── PdfParseService.java          # PDF 文档解析服务
│
├── src/main/resources/
│   ├── application.yml                   # 应用配置
│   ├── static/                           # 静态资源
│   │   ├── index.html                    # 主页
│   │   └── digital-human.html            # 数字人界面
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
  ai:
    zhipuai:
      api-key: YOUR_ZHIPU_API_KEY    # 替换为你的智谱 API Key
      chat:
        options:
          model: glm-4-flash

milvus:
  endpoint: 127.0.0.1:19530           # Milvus 服务地址
  database: default                    # 数据库名称
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

### 1. 基础聊天

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

### 4. 清除会话

```http
DELETE /api/medical/session/{sessionId}
```

### 5. RAG 知识库管理

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

### 🔐 安全机制 (SafetyGuard)

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
| **Embedding** | ZhipuAI Embedding (1024维) |
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
- [ ] PDF 文档解析入库
- [ ] 多模态支持（语音、图像）
- [ ] 用户画像与个性化推荐

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
