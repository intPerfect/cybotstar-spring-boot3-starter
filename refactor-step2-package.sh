#!/bin/bash
# 更新 package 声明
set -e

BASE_DIR="/d/Users/Projects/CybotStar客户端/cybotstar-spring-boot3-starter/src/main/java/com/brgroup/cybotstar"
cd "$BASE_DIR"

echo "=== 更新 package 声明 ==="

# Core 模块
find core/connection -name "*.java" -exec sed -i 's/^package com\.brgroup\.cybotstar\.connection;/package com.brgroup.cybotstar.core.connection;/' {} \;
find core/model -name "*.java" -exec sed -i 's/^package com\.brgroup\.cybotstar\.model\./package com.brgroup.cybotstar.core.model./' {} \;
find core/util -name "*.java" -exec sed -i 's/^package com\.brgroup\.cybotstar\.util/package com.brgroup.cybotstar.core.util/' {} \;
find core/config -name "*.java" -exec sed -i 's/^package com\.brgroup\.cybotstar\.config;/package com.brgroup.cybotstar.core.config;/' {} \;
find core/exception -name "*.java" -exec sed -i 's/^package com\.brgroup\.cybotstar\.handler;/package com.brgroup.cybotstar.core.exception;/' {} \;

# Agent 模块
find agent/config -name "*.java" -exec sed -i 's/^package com\.brgroup\.cybotstar\.config;/package com.brgroup.cybotstar.agent.config;/' {} \;
find agent/handler -name "*.java" -exec sed -i 's/^package com\.brgroup\.cybotstar\.handler;/package com.brgroup.cybotstar.agent.handler;/' {} \;
find agent/model/request -name "*.java" -exec sed -i 's/^package com\.brgroup\.cybotstar\.agent\.model;/package com.brgroup.cybotstar.agent.model.request;/' {} \;
find agent/model/response -name "*.java" -exec sed -i 's/^package com\.brgroup\.cybotstar\.agent\.model;/package com.brgroup.cybotstar.agent.model.response;/' {} \;
find agent/util -name "*.java" -exec sed -i 's/^package com\.brgroup\.cybotstar\.util\.payload;/package com.brgroup.cybotstar.agent.util;/' {} \;
find agent/util -name "*.java" -exec sed -i 's/^package com\.brgroup\.cybotstar\.agent\.internal;/package com.brgroup.cybotstar.agent.util;/' {} \;

# Flow 模块
find flow/config -name "*.java" -exec sed -i 's/^package com\.brgroup\.cybotstar\.config;/package com.brgroup.cybotstar.flow.config;/' {} \;
find flow/handler -name "*.java" -exec sed -i 's/^package com\.brgroup\.cybotstar\.flow\.model\.handler;/package com.brgroup.cybotstar.flow.handler;/' {} \;
find flow/model -name "*.java" -exec sed -i 's/^package com\.brgroup\.cybotstar\.flow\.model\.vo;/package com.brgroup.cybotstar.flow.model;/' {} \;
find flow/util -name "*.java" -exec sed -i 's/^package com\.brgroup\.cybotstar\.util\.payload;/package com.brgroup.cybotstar.flow.util;/' {} \;

# Spring 模块
find spring/autoconfigure -name "*.java" -exec sed -i 's/^package com\.brgroup\.cybotstar;$/package com.brgroup.cybotstar.spring.autoconfigure;/' {} \;
find spring/annotation -name "*.java" -exec sed -i 's/^package com\.brgroup\.cybotstar\.annotation;/package com.brgroup.cybotstar.spring.annotation;/' {} \;

echo "=== Package 声明更新完成 ==="
