#!/bin/bash
# 分包重构自动化脚本
set -e

BASE_DIR="/d/Users/Projects/CybotStar客户端/cybotstar-spring-boot3-starter/src/main/java/com/brgroup/cybotstar"
cd "$BASE_DIR"

echo "=== 阶段 1: 移动核心模块文件 ==="

# 1.1 移动 connection/ 到 core/connection/
echo "移动 connection..."
cp -r connection/* core/connection/ 2>/dev/null || true

# 1.2 移动 model/ 到 core/model/
echo "移动 model..."
cp -r model/common core/model/ 2>/dev/null || true
cp -r model/ws core/model/ 2>/dev/null || true

# 1.3 移动 util/ 到 core/util/
echo "移动 util..."
cp -r util/CybotStarConstants.java core/util/ 2>/dev/null || true
cp -r util/CybotStarUtils.java core/util/ 2>/dev/null || true
cp -r util/payload core/util/ 2>/dev/null || true

# 1.4 移动共享配置到 core/config/
echo "移动共享配置..."
cp config/CybotStarMultiConfig.java core/config/ 2>/dev/null || true
cp config/CredentialProperties.java core/config/ 2>/dev/null || true
cp config/HttpProperties.java core/config/ 2>/dev/null || true
cp config/LogProperties.java core/config/ 2>/dev/null || true
cp config/WebSocketProperties.java core/config/ 2>/dev/null || true

# 1.5 移动 GenericErrorHandler 到 core/exception/
echo "移动 GenericErrorHandler..."
cp handler/GenericErrorHandler.java core/exception/ 2>/dev/null || true

echo "=== 阶段 2: 移动 Agent 模块文件 ==="

# 2.1 移动 AgentConfig 到 agent/config/
echo "移动 AgentConfig..."
cp config/AgentConfig.java agent/config/ 2>/dev/null || true

# 2.2 移动 ReactiveMessageHandler 到 agent/handler/
echo "移动 ReactiveMessageHandler..."
cp handler/ReactiveMessageHandler.java agent/handler/ 2>/dev/null || true

# 2.3 移动 agent/model/ 文件到 request/ 和 response/
echo "移动 agent model..."
cp agent/model/ExtendedSendOptions.java agent/model/request/ 2>/dev/null || true
cp agent/model/GetConversationHistoryOptions.java agent/model/request/ 2>/dev/null || true
cp agent/model/GetConversationHistoryRequest.java agent/model/request/ 2>/dev/null || true
cp agent/model/MessageParam.java agent/model/request/ 2>/dev/null || true
cp agent/model/ModelOptions.java agent/model/ 2>/dev/null || true

cp agent/model/ChatHistoryItem.java agent/model/response/ 2>/dev/null || true
cp agent/model/ConversationHistoryApiResponse.java agent/model/response/ 2>/dev/null || true
cp agent/model/ConversationHistoryItem.java agent/model/response/ 2>/dev/null || true
cp agent/model/ConversationHistoryResponse.java agent/model/response/ 2>/dev/null || true

# 2.4 移动 PayloadBuilder 和 RequestBuilder 到 agent/util/
echo "移动 agent util..."
cp util/payload/PayloadBuilder.java agent/util/ 2>/dev/null || true
cp agent/internal/RequestBuilder.java agent/util/ 2>/dev/null || true

echo "=== 阶段 3: 移动 Flow 模块文件 ==="

# 3.1 移动 FlowConfig 到 flow/config/
echo "移动 FlowConfig..."
cp config/FlowConfig.java flow/config/ 2>/dev/null || true
cp config/FlowProperties.java flow/config/ 2>/dev/null || true

# 3.2 移动 flow/model/handler/ 到 flow/handler/
echo "移动 flow handler..."
cp flow/model/handler/*.java flow/handler/ 2>/dev/null || true

# 3.3 移动 flow/model/vo/ 到 flow/model/
echo "移动 flow model..."
cp flow/model/vo/*.java flow/model/ 2>/dev/null || true

# 3.4 移动 FlowPayloadBuilder 到 flow/util/
echo "移动 flow util..."
cp util/payload/FlowPayloadBuilder.java flow/util/ 2>/dev/null || true

echo "=== 阶段 4: 移动 Spring 集成模块 ==="

# 4.1 移动 CybotStarAutoConfiguration 到 spring/autoconfigure/
echo "移动 AutoConfiguration..."
cp CybotStarAutoConfiguration.java spring/autoconfigure/ 2>/dev/null || true

# 4.2 移动 annotation/ 到 spring/annotation/
echo "移动 annotations..."
cp annotation/*.java spring/annotation/ 2>/dev/null || true

echo "=== 文件移动完成 ==="
