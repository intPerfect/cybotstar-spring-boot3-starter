# CybotStar Spring Boot 3 Starter 分包优化计划

## 当前包结构分析

### 📊 统计数据
- **总文件数**: 61 个 Java 文件
- **包总数**: 22 个包
- **最大包深度**: 4 层

### 📦 当前包结构

```
com.brgroup.cybotstar/
├── [1] CybotStarAutoConfiguration.java          # Spring Boot 自动配置
│
├── agent/                                        # Agent 客户端模块 (15 files)
│   ├── [1] AgentClient.java                     # 主客户端类
│   ├── exception/                                # 异常 (2 files)
│   │   ├── AgentErrorCode.java
│   │   └── AgentException.java
│   ├── internal/                                 # 内部实现 (1 file)
│   │   └── RequestBuilder.java
│   ├── model/                                    # 数据模型 (9 files)
│   │   ├── ChatHistoryItem.java
│   │   ├── ConversationHistoryApiResponse.java
│   │   ├── ConversationHistoryItem.java
│   │   ├── ConversationHistoryResponse.java
│   │   ├── ExtendedSendOptions.java
│   │   ├── GetConversationHistoryOptions.java
│   │   ├── GetConversationHistoryRequest.java
│   │   ├── MessageParam.java
│   │   └── ModelOptions.java
│   └── session/                                  # 会话管理 (2 files)
│       ├── SessionContext.java
│       └── SessionContextManager.java
│
├── annotation/                                   # 注解 (2 files)
│   ├── CybotStarAgent.java
│   └── CybotStarFlow.java
│
├── config/                                       # 配置类 (8 files)
│   ├── AgentConfig.java
│   ├── CredentialProperties.java
│   ├── CybotStarMultiConfig.java
│   ├── FlowConfig.java
│   ├── FlowProperties.java
│   ├── HttpProperties.java
│   ├── LogProperties.java
│   └── WebSocketProperties.java
│
├── connection/                                   # 连接管理 (3 files)
│   ├── ConnectionManager.java
│   ├── WebSocketConnection.java
│   └── WebSocketConnectionPool.java
│
├── flow/                                         # Flow 客户端模块 (18 files)
│   ├── [1] FlowClient.java                      # 主客户端类
│   ├── exception/                                # 异常 (1 file)
│   │   └── FlowException.java
│   ├── model/                                    # 数据模型 (3 files)
│   │   ├── FlowData.java
│   │   ├── FlowEventType.java
│   │   ├── FlowState.java
│   │   ├── handler/                              # 处理器接口 (3 files)
│   │   │   ├── FlowHandler.java
│   │   │   ├── FlowHandlers.java
│   │   │   └── MessageHandler.java
│   │   └── vo/                                   # 值对象 (8 files)
│   │       ├── FlowDebugVO.java
│   │       ├── FlowEndVO.java
│   │       ├── FlowErrorVO.java
│   │       ├── FlowJumpVO.java
│   │       ├── FlowMessageVO.java
│   │       ├── FlowNodeEnterVO.java
│   │       ├── FlowStartVO.java
│   │       └── FlowWaitingVO.java
│   └── util/                                     # 工具类 (2 files)
│       ├── FlowUtils.java
│       └── FlowVOExtractor.java
│
├── handler/                                      # 消息处理器 (2 files)
│   ├── GenericErrorHandler.java
│   └── ReactiveMessageHandler.java
│
├── model/                                        # 通用数据模型 (7 files)
│   ├── common/                                   # 通用模型 (4 files)
│   │   ├── ConnectionState.java
│   │   ├── ResponseIndex.java
│   │   ├── ResponseType.java
│   │   └── ServerErrorCode.java
│   └── ws/                                       # WebSocket 模型 (3 files)
│       ├── WSPayload.java
│       ├── WSResponse.java
│       └── WSResponseData.java
│
├── tool/                                         # 工具 (1 file)
│   └── TerminalUI.java
│
└── util/                                         # 工具类 (4 files)
    ├── CybotStarConstants.java
    ├── CybotStarUtils.java
    └── payload/                                  # Payload 构建器 (2 files)
        ├── FlowPayloadBuilder.java
        └── PayloadBuilder.java
```

---

## 🔍 问题识别

### 1. **包结构混乱**
- ❌ `handler/` 包与 `flow/model/handler/` 包命名冲突，容易混淆
- ❌ `model/` 包既有通用模型，又有 WebSocket 专用模型，职责不清
- ❌ `util/` 包和 `flow/util/` 包分散，不统一

### 2. **职责不清晰**
- ❌ `handler/ReactiveMessageHandler` 实际上是 Agent 专用的，但放在顶层
- ❌ `handler/GenericErrorHandler` 是通用的，但与 ReactiveMessageHandler 混在一起
- ❌ `util/payload/` 包含 Agent 和 Flow 的 Payload 构建器，应该分开

### 3. **包层级过深**
- ❌ `flow/model/handler/` 和 `flow/model/vo/` 层级过深（4层）
- ❌ `agent/model/` 包含 9 个文件，过于臃肿

### 4. **命名不一致**
- ❌ Agent 使用 `model/`，Flow 使用 `model/vo/`，不统一
- ❌ `internal/` 包只有 1 个文件，命名过于宽泛

### 5. **模块边界不清**
- ❌ `connection/` 包被 Agent 和 Flow 共享，但放在顶层
- ❌ `config/` 包混合了 Agent 和 Flow 的配置

---

## 🎯 优化目标

1. **清晰的模块边界** - Agent 和 Flow 完全独立
2. **统一的命名规范** - 相同职责的包使用相同命名
3. **合理的包层级** - 避免过深或过浅
4. **职责单一** - 每个包只负责一个明确的职责
5. **易于理解** - 新人能快速理解项目结构

---

## 📋 优化方案

### 方案 A：按功能模块重组（推荐）

```
com.brgroup.cybotstar/
├── core/                                         # 核心模块（共享）
│   ├── config/                                   # 配置
│   │   ├── CybotStarMultiConfig.java
│   │   ├── CredentialProperties.java
│   │   ├── HttpProperties.java
│   │   ├── LogProperties.java
│   │   └── WebSocketProperties.java
│   ├── connection/                               # 连接管理
│   │   ├── ConnectionManager.java
│   │   ├── WebSocketConnection.java
│   │   └── WebSocketConnectionPool.java
│   ├── exception/                                # 通用异常
│   │   └── GenericErrorHandler.java
│   ├── model/                                    # 通用模型
│   │   ├── ConnectionState.java
│   │   ├── ResponseIndex.java
│   │   ├── ResponseType.java
│   │   ├── ServerErrorCode.java
│   │   ├── WSPayload.java
│   │   ├── WSResponse.java
│   │   └── WSResponseData.java
│   └── util/                                     # 通用工具
│       ├── CybotStarConstants.java
│       └── CybotStarUtils.java
│
├── agent/                                        # Agent 模块
│   ├── AgentClient.java                         # 主客户端
│   ├── config/                                   # Agent 配置
│   │   └── AgentConfig.java
│   ├── exception/                                # Agent 异常
│   │   ├── AgentErrorCode.java
│   │   └── AgentException.java
│   ├── handler/                                  # 消息处理器
│   │   └── ReactiveMessageHandler.java
│   ├── model/                                    # 数据模型
│   │   ├── request/                              # 请求模型
│   │   │   ├── ExtendedSendOptions.java
│   │   │   ├── GetConversationHistoryOptions.java
│   │   │   ├── GetConversationHistoryRequest.java
│   │   │   └── MessageParam.java
│   │   ├── response/                             # 响应模型
│   │   │   ├── ChatHistoryItem.java
│   │   │   ├── ConversationHistoryApiResponse.java
│   │   │   ├── ConversationHistoryItem.java
│   │   │   └── ConversationHistoryResponse.java
│   │   └── ModelOptions.java
│   ├── session/                                  # 会话管理
│   │   ├── SessionContext.java
│   │   └── SessionContextManager.java
│   └── util/                                     # Agent 工具
│       ├── PayloadBuilder.java
│       └── RequestBuilder.java
│
├── flow/                                         # Flow 模块
│   ├── FlowClient.java                          # 主客户端
│   ├── config/                                   # Flow 配置
│   │   ├── FlowConfig.java
│   │   └── FlowProperties.java
│   ├── exception/                                # Flow 异常
│   │   └── FlowException.java
│   ├── handler/                                  # 事件处理器
│   │   ├── FlowHandler.java
│   │   ├── FlowHandlers.java
│   │   └── MessageHandler.java
│   ├── model/                                    # 数据模型
│   │   ├── FlowData.java
│   │   ├── FlowEventType.java
│   │   ├── FlowState.java
│   │   ├── FlowDebugVO.java
│   │   ├── FlowEndVO.java
│   │   ├── FlowErrorVO.java
│   │   ├── FlowJumpVO.java
│   │   ├── FlowMessageVO.java
│   │   ├── FlowNodeEnterVO.java
│   │   ├── FlowStartVO.java
│   │   └── FlowWaitingVO.java
│   └── util/                                     # Flow 工具
│       ├── FlowPayloadBuilder.java
│       ├── FlowUtils.java
│       └── FlowVOExtractor.java
│
├── spring/                                       # Spring 集成
│   ├── autoconfigure/                            # 自动配置
│   │   └── CybotStarAutoConfiguration.java
│   └── annotation/                               # 注解
│       ├── CybotStarAgent.java
│       └── CybotStarFlow.java
│
└── tool/                                         # 开发工具
    └── TerminalUI.java
```

**优点：**
- ✅ 清晰的模块边界（core、agent、flow、spring）
- ✅ 统一的包结构（每个模块都有 config、exception、model、util）
- ✅ 职责单一（每个包只负责一个明确的职责）
- ✅ 易于扩展（新增模块只需复制结构）

**缺点：**
- ⚠️ 需要大量移动文件
- ⚠️ 可能影响现有代码的 import

---

### 方案 B：最小化调整（保守）

```
com.brgroup.cybotstar/
├── [保持不变] CybotStarAutoConfiguration.java
│
├── agent/
│   ├── [保持不变] AgentClient.java
│   ├── config/                                   # 新增：Agent 配置
│   │   └── AgentConfig.java                     # 从 config/ 移动
│   ├── exception/                                # 保持不变
│   ├── handler/                                  # 新增：消息处理器
│   │   └── ReactiveMessageHandler.java          # 从 handler/ 移动
│   ├── model/
│   │   ├── request/                              # 新增：请求模型子包
│   │   └── response/                             # 新增：响应模型子包
│   ├── session/                                  # 保持不变
│   └── util/                                     # 新增：Agent 工具
│       ├── PayloadBuilder.java                  # 从 util/payload/ 移动
│       └── RequestBuilder.java                  # 从 agent/internal/ 移动
│
├── flow/
│   ├── [保持不变] FlowClient.java
│   ├── config/                                   # 新增：Flow 配置
│   │   ├── FlowConfig.java                      # 从 config/ 移动
│   │   └── FlowProperties.java                  # 从 config/ 移动
│   ├── exception/                                # 保持不变
│   ├── handler/                                  # 重命名：从 model/handler/
│   ├── model/                                    # 扁平化：合并 model/ 和 model/vo/
│   └── util/                                     # 保持不变
│       └── FlowPayloadBuilder.java              # 从 util/payload/ 移动
│
├── core/                                         # 新增：核心共享模块
│   ├── config/                                   # 共享配置
│   │   ├── CybotStarMultiConfig.java
│   │   ├── CredentialProperties.java
│   │   ├── HttpProperties.java
│   │   ├── LogProperties.java
│   │   └── WebSocketProperties.java
│   ├── connection/                               # 从顶层移动
│   ├── exception/                                # 新增：通用异常
│   │   └── GenericErrorHandler.java            # 从 handler/ 移动
│   ├── model/                                    # 从顶层 model/ 移动
│   └── util/                                     # 从顶层 util/ 移动
│
├── annotation/                                   # 保持不变
└── tool/                                         # 保持不变
```

**优点：**
- ✅ 改动较小，风险低
- ✅ 保留大部分现有结构
- ✅ 解决主要问题（命名冲突、职责不清）

**缺点：**
- ⚠️ 仍然有一些不够清晰的地方
- ⚠️ 包层级不够统一

---

### 方案 C：领域驱动设计（DDD）

```
com.brgroup.cybotstar/
├── domain/                                       # 领域层
│   ├── agent/                                    # Agent 领域
│   │   ├── AgentClient.java
│   │   ├── model/
│   │   ├── service/
│   │   └── repository/
│   └── flow/                                     # Flow 领域
│       ├── FlowClient.java
│       ├── model/
│       └── service/
│
├── infrastructure/                               # 基础设施层
│   ├── connection/
│   ├── config/
│   └── persistence/
│
├── application/                                  # 应用层
│   ├── service/
│   └── dto/
│
└── interfaces/                                   # 接口层
    ├── spring/
    └── annotation/
```

**优点：**
- ✅ 符合 DDD 最佳实践
- ✅ 清晰的分层架构

**缺点：**
- ❌ 过度设计，不适合 SDK 项目
- ❌ 改动太大，风险高

---

## 🎯 推荐方案：方案 A（按功能模块重组）

### 理由
1. **清晰的模块边界** - core、agent、flow、spring 四大模块
2. **统一的包结构** - 每个模块都有相同的子包结构
3. **易于理解** - 新人能快速找到对应的代码
4. **易于扩展** - 未来新增模块（如 chat、assistant）只需复制结构
5. **符合 Spring Boot Starter 最佳实践**

---

## 📝 实施步骤

### 阶段 1：准备工作（1小时）
1. ✅ 创建分包优化计划文档
2. ⬜ 备份当前代码（git commit）
3. ⬜ 创建新的包结构（空目录）
4. ⬜ 编写自动化迁移脚本

### 阶段 2：核心模块迁移（2小时）
1. ⬜ 创建 `core/` 包
2. ⬜ 移动 `connection/` → `core/connection/`
3. ⬜ 移动 `model/` → `core/model/`
4. ⬜ 移动 `util/` → `core/util/`
5. ⬜ 移动共享配置 → `core/config/`
6. ⬜ 更新所有 import 语句
7. ⬜ 编译验证

### 阶段 3：Agent 模块重组（2小时）
1. ⬜ 创建 `agent/config/`
2. ⬜ 移动 `AgentConfig.java`
3. ⬜ 创建 `agent/handler/`
4. ⬜ 移动 `ReactiveMessageHandler.java`
5. ⬜ 重组 `agent/model/` 为 `request/` 和 `response/`
6. ⬜ 创建 `agent/util/`
7. ⬜ 移动 `PayloadBuilder.java` 和 `RequestBuilder.java`
8. ⬜ 更新所有 import 语句
9. ⬜ 编译验证

### 阶段 4：Flow 模块重组（2小时）
1. ⬜ 创建 `flow/config/`
2. ⬜ 移动 `FlowConfig.java` 和 `FlowProperties.java`
3. ⬜ 重命名 `flow/model/handler/` → `flow/handler/`
4. ⬜ 扁平化 `flow/model/vo/` → `flow/model/`
5. ⬜ 移动 `FlowPayloadBuilder.java` → `flow/util/`
6. ⬜ 更新所有 import 语句
7. ⬜ 编译验证

### 阶段 5：Spring 集成模块（1小时）
1. ⬜ 创建 `spring/` 包
2. ⬜ 创建 `spring/autoconfigure/`
3. ⬜ 移动 `CybotStarAutoConfiguration.java`
4. ⬜ 移动 `annotation/` → `spring/annotation/`
5. ⬜ 更新 `spring.factories` 配置
6. ⬜ 更新所有 import 语句
7. ⬜ 编译验证

### 阶段 6：测试与验证（2小时）
1. ⬜ 运行所有单元测试
2. ⬜ 运行集成测试
3. ⬜ 验证示例代码
4. ⬜ 更新 README 文档
5. ⬜ 更新 JavaDoc

### 阶段 7：清理与优化（1小时）
1. ⬜ 删除空包
2. ⬜ 优化 import 语句
3. ⬜ 更新 package-info.java
4. ⬜ 生成迁移指南

---

## 📊 影响评估

### 代码变更
- **移动文件数**: 约 50 个文件
- **修改 import**: 约 200 处
- **新增包**: 约 15 个
- **删除包**: 约 5 个

### 风险评估
- **编译风险**: 🟡 中等（需要更新大量 import）
- **运行时风险**: 🟢 低（只是包结构调整，不改逻辑）
- **向后兼容**: 🔴 高（需要提供迁移指南）

### 时间估算
- **总工时**: 约 10-12 小时
- **建议分批**: 分 3-4 次提交，每次 2-3 小时

---

## 🔄 回滚方案

如果迁移过程中出现问题：
1. 使用 `git reset --hard` 回滚到迁移前的提交
2. 或使用 `git revert` 撤销迁移提交
3. 保留迁移脚本，修复问题后重新执行

---

## 📚 迁移指南（用户）

### 对于使用者
```java
// 旧的 import（迁移前）
import com.brgroup.cybotstar.agent.AgentClient;
import com.brgroup.cybotstar.config.AgentConfig;
import com.brgroup.cybotstar.model.ws.WSPayload;

// 新的 import（迁移后）
import com.brgroup.cybotstar.agent.AgentClient;
import com.brgroup.cybotstar.agent.config.AgentConfig;
import com.brgroup.cybotstar.core.model.WSPayload;
```

### 自动迁移工具
提供 Maven 插件或脚本，自动更新用户代码的 import 语句。

---

## ✅ 验收标准

1. ✅ 所有文件都在正确的包中
2. ✅ 没有循环依赖
3. ✅ 编译通过，无警告
4. ✅ 所有测试通过
5. ✅ 文档已更新
6. ✅ 迁移指南已提供

---

## 🎯 下一步行动

**请确认：**
1. 是否采用方案 A（按功能模块重组）？
2. 是否现在开始实施？
3. 是否需要调整方案？

**如果确认，我将：**
1. 创建 git 分支 `feature/package-refactoring`
2. 开始阶段 1：准备工作
3. 逐步执行迁移计划

---

**计划制定完成！** 📋
