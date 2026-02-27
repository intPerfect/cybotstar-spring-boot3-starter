#!/bin/bash
# 批量更新 import 语句
set -e

BASE_DIR="/d/Users/Projects/CybotStar客户端/cybotstar-spring-boot3-starter/src/main/java"
cd "$BASE_DIR"

echo "=== 批量更新 import 语句 ==="

# 更新所有 Java 文件的 import
find . -name "*.java" -type f -exec sed -i '
# Core 模块
s/import com\.brgroup\.cybotstar\.connection\./import com.brgroup.cybotstar.core.connection./g
s/import com\.brgroup\.cybotstar\.model\.common\./import com.brgroup.cybotstar.core.model.common./g
s/import com\.brgroup\.cybotstar\.model\.ws\./import com.brgroup.cybotstar.core.model.ws./g
s/import com\.brgroup\.cybotstar\.util\.CybotStar/import com.brgroup.cybotstar.core.util.CybotStar/g
s/import com\.brgroup\.cybotstar\.util\.payload\.PayloadBuilder;/import com.brgroup.cybotstar.agent.util.PayloadBuilder;/g
s/import com\.brgroup\.cybotstar\.util\.payload\.FlowPayloadBuilder;/import com.brgroup.cybotstar.flow.util.FlowPayloadBuilder;/g
s/import com\.brgroup\.cybotstar\.handler\.GenericErrorHandler;/import com.brgroup.cybotstar.core.exception.GenericErrorHandler;/g

# Core config
s/import com\.brgroup\.cybotstar\.config\.CybotStarMultiConfig;/import com.brgroup.cybotstar.core.config.CybotStarMultiConfig;/g
s/import com\.brgroup\.cybotstar\.config\.CredentialProperties;/import com.brgroup.cybotstar.core.config.CredentialProperties;/g
s/import com\.brgroup\.cybotstar\.config\.HttpProperties;/import com.brgroup.cybotstar.core.config.HttpProperties;/g
s/import com\.brgroup\.cybotstar\.config\.LogProperties;/import com.brgroup.cybotstar.core.config.LogProperties;/g
s/import com\.brgroup\.cybotstar\.config\.WebSocketProperties;/import com.brgroup.cybotstar.core.config.WebSocketProperties;/g

# Agent 模块
s/import com\.brgroup\.cybotstar\.config\.AgentConfig;/import com.brgroup.cybotstar.agent.config.AgentConfig;/g
s/import com\.brgroup\.cybotstar\.handler\.ReactiveMessageHandler;/import com.brgroup.cybotstar.agent.handler.ReactiveMessageHandler;/g
s/import com\.brgroup\.cybotstar\.agent\.internal\.RequestBuilder;/import com.brgroup.cybotstar.agent.util.RequestBuilder;/g
s/import com\.brgroup\.cybotstar\.agent\.model\.ExtendedSendOptions;/import com.brgroup.cybotstar.agent.model.request.ExtendedSendOptions;/g
s/import com\.brgroup\.cybotstar\.agent\.model\.GetConversationHistoryOptions;/import com.brgroup.cybotstar.agent.model.request.GetConversationHistoryOptions;/g
s/import com\.brgroup\.cybotstar\.agent\.model\.GetConversationHistoryRequest;/import com.brgroup.cybotstar.agent.model.request.GetConversationHistoryRequest;/g
s/import com\.brgroup\.cybotstar\.agent\.model\.MessageParam;/import com.brgroup.cybotstar.agent.model.request.MessageParam;/g
s/import com\.brgroup\.cybotstar\.agent\.model\.ChatHistoryItem;/import com.brgroup.cybotstar.agent.model.response.ChatHistoryItem;/g
s/import com\.brgroup\.cybotstar\.agent\.model\.ConversationHistoryApiResponse;/import com.brgroup.cybotstar.agent.model.response.ConversationHistoryApiResponse;/g
s/import com\.brgroup\.cybotstar\.agent\.model\.ConversationHistoryItem;/import com.brgroup.cybotstar.agent.model.response.ConversationHistoryItem;/g
s/import com\.brgroup\.cybotstar\.agent\.model\.ConversationHistoryResponse;/import com.brgroup.cybotstar.agent.model.response.ConversationHistoryResponse;/g

# Flow 模块
s/import com\.brgroup\.cybotstar\.config\.FlowConfig;/import com.brgroup.cybotstar.flow.config.FlowConfig;/g
s/import com\.brgroup\.cybotstar\.config\.FlowProperties;/import com.brgroup.cybotstar.flow.config.FlowProperties;/g
s/import com\.brgroup\.cybotstar\.flow\.model\.handler\./import com.brgroup.cybotstar.flow.handler./g
s/import com\.brgroup\.cybotstar\.flow\.model\.vo\./import com.brgroup.cybotstar.flow.model./g

# Spring 模块
s/import com\.brgroup\.cybotstar\.CybotStarAutoConfiguration;/import com.brgroup.cybotstar.spring.autoconfigure.CybotStarAutoConfiguration;/g
s/import com\.brgroup\.cybotstar\.annotation\./import com.brgroup.cybotstar.spring.annotation./g
' {} \;

echo "=== Import 语句更新完成 ==="
