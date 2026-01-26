# CybotStar Spring Boot 3 Starter

百融智能体 Spring Boot 3 集成 SDK - 快速集成 AI 对话能力到您的 Spring Boot 应用。

## 🚀 快速开始

### 1. 添加依赖

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.br</groupId>
    <artifactId>cybotstar-spring-boot3-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置应用

在 `application.yml` 中配置（**重要：必须使用 `cybotstar.agents` 格式**）：

```yaml
cybotstar:
  agents:
    # 配置名称，可以自定义，如：my-agent、finance-agent 等
    my-agent:
      credentials:
        robot-key: your-robot-key
        robot-token: your-robot-token
        username: your-username
      websocket:
        url: wss://www.cybotstar.cn/openapi/v2/ws/dialog/
        # 可选配置
        timeout: 5000              # 连接超时时间（毫秒）
        max-retries: 3             # 最大重试次数
        retry-interval: 1000       # 重试间隔（毫秒）
        auto-reconnect: true       # 自动重连
        heartbeat-interval: 30000  # 心跳间隔（毫秒），设为 0 禁用心跳
      http:
        url: https://www.cybotstar.cn/openapi/v2/  # HTTP API URL
        connect-timeout: 30000     # 连接超时（毫秒）
        read-timeout: 30000        # 读取超时（毫秒）
        write-timeout: 30000       # 写入超时（毫秒）
      log:
        log-level: info            # 日志等级
```

### 3. 开始使用

**方式一：通过 Spring 注入（推荐）**

```java
import com.brgroup.cybotstar.agent.AgentClient;
import com.brgroup.cybotstar.annotation.CybotStarAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    // 单个配置时，可以直接注入（会自动创建名为 "agentClient" 的bean）
    @Autowired
    private AgentClient agentClient;

    // 或者使用注解指定配置名称
    @Autowired
    @CybotStarAgent("my-agent")
    private AgentClient myAgentClient;

    public void chat() {
        // 发送消息并获取回复
        String response = agentClient.prompt("你好").send();
        System.out.println("AI 回复: " + response);
    }
}
```

**方式二：手动创建（不推荐，但可用）**

```java
import com.brgroup.cybotstar.agent.AgentClient;
import com.brgroup.cybotstar.config.AgentConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    @Autowired
    private AgentConfig agentConfig;  // 需要先配置 cybotstar.agents

    public void chat() {
        AgentClient client = new AgentClient(agentConfig);
        try {
            String response = client.prompt("你好").send();
            System.out.println("AI 回复: " + response);
        } finally {
            client.close();  // 关闭连接
        }
    }
}
```

## 🎯 常用功能

### 1. 流式响应（推荐）

实时显示 AI 回复，提升用户体验：

```java
AgentClient client = new AgentClient(agentConfig);

try {
    AgentStream stream = client
        .prompt("介绍一下你自己")
        .session("my-session")
        .onChunk(chunk -> System.out.print(chunk))  // 实时输出
        .stream();
    
    stream.done().join();  // 等待完成
} finally {
    client.close();
}
```

### 2. 多轮对话

使用相同的 `session()` 保持对话上下文：

```java
AgentClient client = new AgentClient(agentConfig);

try {
    // 设置会话ID
    client.session("user-123");
    
    // 第一轮
    client.prompt("我叫张三").stream().done().join();
    
    // 第二轮（AI 会记住之前的内容）
    String response = client.prompt("我的名字是什么？").send();
    System.out.println(response);  // AI 会回答"张三"
} finally {
    client.close();
}
```

### 3. 设置模型参数

```java
import model.agent.com.brgroup.cybotstar.ModelOptions;

ModelOptions options = ModelOptions.builder()
        .temperature(0.7)    // 控制随机性（0-1）
        .maxTokens(2000)      // 最大生成token数
        .build();

AgentStream stream = client
        .prompt("你的问题")
        .option(options)
        .stream();
```

### 4. 手动构建对话历史

```java
import model.agent.com.brgroup.cybotstar.MessageParam;

import java.util.Arrays;

// 构建消息历史（注意：不需要包含当前问题，prompt() 会自动添加）
List<MessageParam> messages = Arrays.asList(
        system("你是一个旅游顾问，能够回答关于北京的问题"),
        user("北京有什么好玩的景点？"),
        assistant("北京有很多著名景点，比如故宫、天安门、长城、颐和园等。故宫是明清两朝的皇宫，非常值得一游。")
);

        // 使用历史消息，prompt() 会自动将当前问题作为 user 消息添加
        String response = client
                .prompt("那故宫的门票多少钱？")
                .messages(messages)
                .send();
```

## ⚙️ 配置说明

### 必需配置

**重要：必须使用 `cybotstar.agents.{name}` 格式**

```yaml
cybotstar:
  agents:
    my-agent:  # 配置名称，可以自定义
      credentials:
        robot-key: your-robot-key      # 机器人 Key
        robot-token: your-robot-token  # 机器人 Token
        username: your-username        # 用户名
      websocket:
        url: wss://www.cybotstar.cn/openapi/v2/ws/dialog/  # WebSocket 连接地址
```

### 完整配置（包含可选项）

```yaml
cybotstar:
  agents:
    my-agent:  # 配置名称
      credentials:
        robot-key: your-robot-key
        robot-token: your-robot-token
        username: your-username
      websocket:
        url: wss://www.cybotstar.cn/openapi/v2/ws/dialog/
        timeout: 5000              # 连接超时时间（毫秒），默认 5000
        max-retries: 3             # 最大重试次数，默认 3
        retry-interval: 1000       # 重试间隔（毫秒），默认 1000
        auto-reconnect: true       # 自动重连，默认 true
        heartbeat-interval: 30000  # 心跳间隔（毫秒），默认 30000，设为 0 禁用心跳
      http:
        url: https://www.cybotstar.cn/openapi/v2/  # HTTP API URL，默认值
        connect-timeout: 30000     # 连接超时（毫秒），默认 30000
        read-timeout: 30000        # 读取超时（毫秒），默认 30000
        write-timeout: 30000       # 写入超时（毫秒），默认 30000
      log:
        log-level: info            # 日志等级，默认 info
```

### 多配置支持

可以在同一个配置文件中配置多个 Agent：

```yaml
cybotstar:
  agents:
    finance-agent:
      credentials:
        robot-key: xxx
        robot-token: xxx
        username: user1
      websocket:
        url: wss://www.cybotstar.cn/openapi/v2/ws/dialog/
    
    customer-service-agent:
      credentials:
        robot-key: yyy
        robot-token: yyy
        username: user2
      websocket:
        url: wss://www.cybotstar.cn/openapi/v2/ws/dialog/
```

使用时通过 `@CybotStarAgent` 注解指定：

```java
@Autowired
@CybotStarAgent("finance-agent")
private AgentClient financeAgent;

@Autowired
@CybotStarAgent("customer-service-agent")
private AgentClient customerServiceAgent;
```

## ❓ 常见问题

### Q: 启动时报错 "A component required a bean of type 'com.brgroup.cybotstar.agent.AgentClient' that could not be found"

**原因：** 没有配置 `cybotstar.agents`，或者配置格式不正确。

**解决方案：**
1. 确保在 `application.yml` 中配置了 `cybotstar.agents`（注意是 `cybotstar.agents`，不是 `agent`）
2. 配置格式示例：
   ```yaml
   cybotstar:
     agents:
       my-agent:  # 配置名称
         credentials:
           robot-key: xxx
           robot-token: xxx
           username: xxx
         websocket:
           url: wss://www.cybotstar.cn/openapi/v2/ws/dialog/
   ```
3. 如果配置了多个 Agent，需要使用 `@CybotStarAgent("配置名称")` 注解指定

### Q: `send()` 和 `stream()` 有什么区别？

- `send()`：等待完整响应后一次性返回 `String`
- `stream()`：返回 `AgentStream`，可以实时接收数据块

```java
// 方式1：一次性获取
String response = client.prompt("你好").send();

// 方式2：流式接收（推荐）
AgentStream stream = client
    .prompt("你好")
    .onChunk(chunk -> System.out.print(chunk))
    .stream();
stream.done().join();
```

### Q: 如何管理多个用户的对话？

为每个用户使用不同的 `session()` ID：

```java
// 用户1
client.session("user-001").prompt("你好").send();

// 用户2
client.session("user-002").prompt("你好").send();
```

### Q: 如何获取对话历史？

```java
SessionContext context = client.getSessionContext("session-id");
List<MessageParam> history = context.getHistoryMsgs();
```

## 📖 更多资源

- 完整示例代码：`src/test/java/com/br/cybotstar/examples/`
- [TypeScript SDK](../agent-sdk/) - 前端版本
- [百融智能体平台](https://www.cybotstar.cn/)

---

**祝您使用愉快！** 🎉
