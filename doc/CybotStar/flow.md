# 对话流接口文档

## 1. 接口概述

- **接口名称**：机器人对话流
- **版本号**：v1.0.0

## 2. 接口信息

| 类型 | URL |
|------|-----|
| 请求URL | `https://www.cybotstar.cn/openapi/v2/conversation/dialog/` |
| WebSocket | `wss://www.cybotstar.cn/openapi/v1/ws/dialog/` |

> **注**：对话流开放接口使用 v2 版本 openapi，其他对话功能兼容 v1 版本，具体可参考《机器人对话》相关接口文档。

## 3. 请求参数

### 请求体字段

| 参数名称 | 类型 | 是否必填 | 描述 |
|----------|------|----------|------|
| `cybertron-robot-key` | string | 是 | 机器人标识 |
| `cybertron-robot-token` | string | 是 | 机器人口令 |
| `username` | string | 是 | 赛博坦用户账号 |
| `segment_code` | string | 否 | 会话编码，可以通过该参数切分对话历史，该参数获取可以调用获取会话信息、接口创建或者获取会话历史。如果不传该字段，系统会通过用户id和robot id查询最新会话，如果查不到，则会自动创建 |
| `question` | string | 否 | 输入问题 |
| `open_flow_trigger` | string | 否 | auto模型兼容正常对话和意图识别；direct模式需指定具体flow_uuid直接调用 |
| `open_flow_uuid` | string | 否 | 指定运行的具体flow流；当`open_flow_trigger`为direct时为必填 |
| `open_flow_node_uuid` | string | 否 | 指定flow中的某个具体node运行 |
| `open_flow_node_inputs` | object | 否 | 指定flow中某个具体node的入参 |
| `open_flow_debug` | int | 否 | 1是，0否；默认0，非debug模式运行flow（仅打印结点回复和部分重要信息） |

## 4. 响应字段说明

### 响应字段说明

| 字段名称 | 类型 | 描述 |
|----------|------|------|
| `code` | string | 6位数字组成的字符串；000000为正常；400开头为出现错误 |
| `message` | string | 状态说明 |
| `type` | string | 报文类型；string时data为字符串；json时data为json对象；flow时data为flow输出json对象 |
| `index` | int | 参考v1接口 |
| `flow_name` | string | 当前运行对话流名称 |
| `node_type` | string | 当前运行结点类型 |
| `node_title` | string | 当前运行结点名称 |
| `node_waiting_input` | int | 1为等待用户输出；0为非等待。 |
| `data` | object | 消息体 |
| `data.history` | array | 对话历史，数组形式，数组中每个元素为一个节点的人机交互 |
| `data.output` | object | 当前结点的flow输出 |
| `data.answer` | string | 当前结点输出（当前仅提供字符串输出） |
| `data.content_type` | string | 为text时表示answer为字符串输出（当前仅提供text类型） |
| `data.flow_stage` | string | 当前flow的状态（与code对应） |
| `data.code` | string | flow输出的数据信息（debug模式或正常输出） |
| `data.node_stream` | int | 1或0；1为流式，需要answer拼接成最终的 |
| `data.node_answer_index` | int | 为stream时表示answer当前拼接序号 |
| `data.node_answer_finish` | string | y或n；为y时表示当前结点输出结束 |
| `data.cur_node_id` | string | 当前结点的id |
| `data.parent_node_id` | string | 当前结点的父结点id |

### 响应消息体中code码说明

| code码 | 说明 |
|--------|------|
| 0 | 正常输出 |
| 400000 | 出现错误 |
| 2000 | flow进入 |
| 2001 | flow退出 |
| 2002 | node进入 |
| 2003 | node调试信息输出（仅debug模式） |
| 2004 | flow等待用户输入后才能继续运行 |
| 2005 | flow引擎当前运行轮次结束 |

## 5. 示例

### 第一轮

#### 【客户端→服务端】用户通过flow_uuid执行指定flow

```json
{
   "question": "",
   "username": "me",
   "cybertron-robot-key": "PL1qFV************lhkA0NSPo=",
   "cybertron-robot-token": "MTcwMjg************************************ZdWJ3ckd1ODQ9",
   "segment_code": "cd0f14c3-801c-4b2d-8882-395e4ee7b9f5",
   "open_flow_trigger": "direct",
   "open_flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
   "open_flow_node_uuid": "",
   "open_flow_node_inputs": {},
   "open_flow_debug": 1
}
```

#### 【服务端→客户端】任务接收成功

```json
{
  "code": "000000",
  "message": "task send success,",
  "type": "json",
  "index": -2,
  "data": {
    "task_id": "7e912afca57b11ef8652004238b4a21d"
  }
}
```

#### 【服务端→客户端】问题发送成功，生成唯一对话id（功能见v1版接口)

```json
{
  "code": "000000",
  "message": "send question success",
  "index": -1,
  "type": "json",
  "data": {
    "question": "",
    "dialog_id": "1858406156273270784"
  }
}
```

#### 【服务端→客户端】服务端进行flow执行

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858406156273270784,
  "type": "flow",
  "index": 0,
  "data": {
      "final": true,
      "id": null,
      "response_create_time": "2024-11-18 15:05",
      "response_create_time_timestamp": "1731913529981",
      "response_update_time": "2024-11-18 15:05",
      "response_update_time_timestamp": "1731913529981",
      "flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
      "history": [],
      "output": {},
      "answer": "flow_enter",
      "content_type": "text",
      "session_id": "7edd9595-a57b-11ef-bce8-004238b4a21d",
      "flow_stage": "flow_enter",
      "code": "002000",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0,
      "target_node_id": "start00000000000000000000",
      "source_node_id": "start00000000000000000000",
      "cur_node_id": "start00000000000000000000",
      "parent_node_id": "start00000000000000000000"
  },
  "finish": "y",
  "cur_node_id": "start00000000000000000000",
  "node_id": "start00000000000000000000",
  "debug": 1,
  "node_developer": 0,
  "flow_name": "客户信息收集小助手",
  "node_type": "start",
  "node_title": "客户信息收集小助手",
  "parent_node_id": "start00000000000000000000",
  "node_waiting_input": 0
}
```

#### 【服务端→客户端】flow执行过程中node跳转信息，从start节点跳转到节点1

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858406156273270784,
  "type": "flow",
  "index": 0,
  "data": {
      "final": true,
      "id": null,
      "response_create_time": "2024-11-18 15:05",
      "response_create_time_timestamp": "1731913529981",
      "response_update_time": "2024-11-18 15:05",
      "response_update_time_timestamp": "1731913529981",
      "flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
      "history": [],
      "output": {
          "inputs": {},
          "variables": {},
          "entities": {},
          "robot_user_asking": "",
          "user_robot_replying": "",
          "robot_user_replying": ""
      },
      "answer": "node_id: a93311b9-7e1b-40d3-a0e5-94e26e2d9748 enter by prev_node_id: start00000000000000000000",
      "content_type": "text",
      "session_id": "7edd9595-a57b-11ef-bce8-004238b4a21d",
      "flow_stage": "flow_running",
      "code": "002002",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0,
      "target_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
      "source_node_id": "start00000000000000000000",
      "cur_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
      "parent_node_id": "start00000000000000000000"
  },
  "finish": "y",
  "cur_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
  "node_id": "start00000000000000000000",
  "debug": 1,
  "node_developer": 0,
  "flow_name": "客户信息收集小助手",
  "node_type": "answer",
  "node_title": null,
  "node_waiting": false,
  "parent_node_id": "start00000000000000000000",
  "node_waiting_input": 0
}
```

#### 【服务端→客户端】节点1执行，回复客户端内容见data.answer

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858406156273270784,
  "type": "flow",
  "index": 0,
  "data": {
      "final": true,
      "id": null,
      "response_create_time": "2024-11-18 15:05",
      "response_create_time_timestamp": "1731913529981",
      "response_update_time": "2024-11-18 15:05",
      "response_update_time_timestamp": "1731913529981",
      "flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
      "history": [
          {
              "timestamp": 1731913531091,
              "time_date": "20241118150531091582",
              "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
              "robot_user_replying": "你好，欢迎您来我公司办理业务！现在将对您的姓名和联系方式进行收集。我们将会保密。请放心。",
              "robot_user_asking": "",
              "user_robot_replying": ""
          }
      ],
      "output": {
          "inputs": {},
          "variables": {},
          "entities": {},
          "robot_user_asking": "",
          "user_robot_replying": "",
          "robot_user_replying": "你好，欢迎您来我公司办理业务！现在将对您的姓名和联系方式进行收集。我们将会保密。请放心。"
      },
      "answer": "你好，欢迎您来我公司办理业务！现在将对您的姓名和联系方式进行收集。我们将会保密。请放心。",
      "content_type": "text",
      "session_id": "7edd9595-a57b-11ef-bce8-004238b4a21d",
      "flow_stage": "flow_running",
      "code": "000000",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0,
      "target_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
      "source_node_id": "start00000000000000000000",
      "cur_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
      "parent_node_id": "start00000000000000000000"
  },
  "finish": "y",
  "cur_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
  "node_id": "start00000000000000000000",
  "debug": 1,
  "node_developer": 0,
  "flow_name": "客户信息收集小助手",
  "node_type": "answer",
  "node_title": null,
  "node_waiting": false,
  "parent_node_id": "start00000000000000000000",
  "node_waiting_input": 0
}
```

#### 【服务端→客户端】flow执行过程，节点跳转信息，此为节点1跳转到节点2

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858406156273270784,
  "type": "flow",
  "index": 0,
  "data": {
      "final": true,
      "id": null,
      "response_create_time": "2024-11-18 15:05",
      "response_create_time_timestamp": "1731913529981",
      "response_update_time": "2024-11-18 15:05",
      "response_update_time_timestamp": "1731913529981",
      "flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
      "history": [
          {
              "timestamp": 1731913531091,
              "time_date": "20241118150531091582",
              "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
              "robot_user_replying": "你好，欢迎您来我公司办理业务！现在将对您的姓名和联系方式进行收集。我们将会保密。请放心。",
              "robot_user_asking": "",
              "user_robot_replying": ""
          }
      ],
      "output": {
          "inputs": {},
          "variables": {},
          "entities": {},
          "robot_user_asking": "",
          "user_robot_replying": "",
          "robot_user_replying": ""
      },
      "answer": "node_id: 67a4219c-963d-4e9d-8af9-81e6d3af6095 enter by prev_node_id: a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
      "content_type": "text",
      "session_id": "7edd9595-a57b-11ef-bce8-004238b4a21d",
      "flow_stage": "flow_running",
      "code": "002002",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0,
      "target_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
      "source_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
      "cur_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
      "parent_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748"
  },
  "finish": "y",
  "cur_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
  "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
  "debug": 1,
  "node_developer": 0,
  "flow_name": "客户信息收集小助手",
  "node_type": "parameter_extractor",
  "node_title": null,
  "node_waiting": false,
  "parent_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
  "node_waiting_input": 0
}
```

#### 【服务端→客户端】节点2执行，提示用户输入姓名

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858406156273270784,
  "type": "flow",
  "index": 0,
  "data": {
      "final": true,
      "id": null,
      "response_create_time": "2024-11-18 15:05",
      "response_create_time_timestamp": "1731913529981",
      "response_update_time": "2024-11-18 15:05",
      "response_update_time_timestamp": "1731913529981",
      "flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
      "history": [
          {
              "timestamp": 1731913531091,
              "time_date": "20241118150531091582",
              "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
              "robot_user_replying": "你好，欢迎您来我公司办理业务！现在将对您的姓名和联系方式进行收集。我们将会保密。请放心。",
              "robot_user_asking": "",
              "user_robot_replying": ""
          }
      ],
      "output": {
          "inputs": {},
          "variables": {},
          "entities": {},
          "robot_user_asking": "请提供一下您的姓名",
          "user_robot_replying": "",
          "robot_user_replying": ""
      },
      "answer": "请提供一下您的姓名",
      "content_type": "text",
      "session_id": "7edd9595-a57b-11ef-bce8-004238b4a21d",
      "flow_stage": "flow_running",
      "code": "000000",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0,
      "target_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
      "source_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
      "cur_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
      "parent_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748"
  },
  "finish": "y",
  "cur_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
  "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
  "debug": 1,
  "node_developer": 0,
  "flow_name": "客户信息收集小助手",
  "node_type": "parameter_extractor",
  "node_title": null,
  "node_waiting": false,
  "parent_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
  "node_waiting_input": 0
}
```

#### 【服务端→客户端】服务端输出客户端通知客户端输入

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858406156273270784,
  "type": "flow",
  "index": 0,
  "data": {
      "final": true,
      "id": null,
      "response_create_time": "2024-11-18 15:05",
      "response_create_time_timestamp": "1731913529981",
      "response_update_time": "2024-11-18 15:05",
      "response_update_time_timestamp": "1731913529981",
      "flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
      "history": [
          {
              "timestamp": 1731913531091,
              "time_date": "20241118150531091582",
              "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
              "robot_user_replying": "你好，欢迎您来我公司办理业务！现在将对您的姓名和联系方式进行收集。我们将会保密。请放心。",
              "robot_user_asking": "",
              "user_robot_replying": ""
          }
      ],
      "output": {
          "inputs": {},
          "variables": {},
          "entities": {},
          "robot_user_asking": "请提供一下您的姓名",
          "user_robot_replying": "",
          "robot_user_replying": ""
      },
      "answer": "node_waiting_input",
      "content_type": "text",
      "session_id": "7edd9595-a57b-11ef-bce8-004238b4a21d",
      "flow_stage": "flow_running",
      "code": "002004",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0,
      "target_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
      "source_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
      "cur_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
      "parent_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748"
  },
  "finish": "y",
  "cur_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
  "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
  "debug": 1,
  "node_developer": 0,
  "flow_name": "客户信息收集小助手",
  "node_type": "parameter_extractor",
  "node_title": null,
  "node_waiting": true,
  "parent_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
  "node_waiting_input": 1
}
```

#### 【服务端→客户端】type为flow且finish为y表示当前轮次flow交互完成

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858406156273270784,
  "type": "flow",
  "index": 0,
  "data": {
      "code": "002005",
      "answer": "current_communication_complete",
      "content_type": "text",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0
  },
  "finish": "y"
}
```

### 第二轮

#### 【客户端→服务端】question为我叫张三，提供flow需要收集的信息

```json
{
  "question": "我叫张三",
  "username": "me",
  "cybertron-robot-key": "PL1qFV************lhkA0NSPo=",
  "cybertron-robot-token": "MTcwMjg************************************ZdWJ3ckd1ODQ9",
  "segment_code": "cd0f14c3-801c-4b2d-8882-395e4ee7b9f5",
  "open_flow_trigger": "direct",
  "open_flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
  "open_flow_node_uuid": "",
  "open_flow_node_inputs": {},
  "open_flow_debug": 1
}
```

#### 【服务端→客户端】服务端收到报文

```json
{
  "code": "000000",
  "message": "task send success,",
  "type": "json",
  "index": -2,
  "data": {
    "task_id": "f15a5e0ba57b11efb0b4004238b4a21d"
  }
}
```

```json
{
  "code": "000000",
  "message": "send question success",
  "index": -1,
  "type": "json",
  "data": {
    "question": "我叫张三",
    "dialog_id": "1858406963072811008"
  }
}
```

#### 【服务端→客户端】服务端回复客户端信息，收集到实体槽位和值的信息

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858406963072811008,
  "type": "flow",
  "index": 0,
  "data": {
      "final": true,
      "id": null,
      "response_create_time": "2024-11-18 15:08",
      "response_create_time_timestamp": "1731913722548",
      "response_update_time": "2024-11-18 15:08",
      "response_update_time_timestamp": "1731913722548",
      "flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
      "history": [
          {
              "timestamp": 1731913531091,
              "time_date": "20241118150531091582",
              "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
              "robot_user_replying": "你好，欢迎您来我公司办理业务！现在将对您的姓名和联系方式进行收集。我们将会保密。请放心。",
              "robot_user_asking": "",
              "user_robot_replying": ""
          },
          {
              "timestamp": 1731913725910,
              "time_date": "20241118150845910619",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我叫张三"
          },
          {
              "timestamp": 1731913725910,
              "time_date": "20241118150845910619",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我叫张三"
          }
      ],
      "output": {
          "inputs": {},
          "variables": {},
          "entities": {
              "customerName": "张三"
          },
          "robot_user_asking": "",
          "user_robot_replying": "我叫张三",
          "robot_user_replying": ""
      },
      "answer": "entity: customerName, value: 张三",
      "content_type": "text",
      "session_id": "7edd9595-a57b-11ef-bce8-004238b4a21d",
      "flow_stage": "flow_running",
      "code": "002003",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0,
      "target_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
      "source_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
      "cur_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
      "parent_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748"
  },
  "finish": "y",
  "cur_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
  "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
  "debug": 1,
  "node_developer": 0,
  "flow_name": "客户信息收集小助手",
  "node_type": "parameter_extractor",
  "node_title": null,
  "node_waiting": false,
  "parent_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
  "node_waiting_input": 0
}
```

#### 【服务端→客户端】节点2执行，提示用户输入手机号

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858406963072811008,
  "type": "flow",
  "index": 0,
  "data": {
      "final": true,
      "id": null,
      "response_create_time": "2024-11-18 15:08",
      "response_create_time_timestamp": "1731913722548",
      "response_update_time": "2024-11-18 15:08",
      "response_update_time_timestamp": "1731913722548",
      "flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
      "history": [
          {
              "timestamp": 1731913531091,
              "time_date": "20241118150531091582",
              "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
              "robot_user_replying": "你好，欢迎您来我公司办理业务！现在将对您的姓名和联系方式进行收集。我们将会保密。请放心。",
              "robot_user_asking": "",
              "user_robot_replying": ""
          },
          {
              "timestamp": 1731913725910,
              "time_date": "20241118150845910619",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我叫张三"
          },
          {
              "timestamp": 1731913725910,
              "time_date": "20241118150845910619",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我叫张三"
          }
      ],
      "output": {
          "inputs": {},
          "variables": {},
          "entities": {
              "customerName": "张三"
          },
          "robot_user_asking": "请提供一下您的手机号",
          "user_robot_replying": "",
          "robot_user_replying": ""
      },
      "answer": "请提供一下您的手机号",
      "content_type": "text",
      "session_id": "7edd9595-a57b-11ef-bce8-004238b4a21d",
      "flow_stage": "flow_running",
      "code": "000000",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0,
      "target_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
      "source_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
      "cur_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
      "parent_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748"
  },
  "finish": "y",
  "cur_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
  "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
  "debug": 1,
  "node_developer": 0,
  "flow_name": "客户信息收集小助手",
  "node_type": "parameter_extractor",
  "node_title": null,
  "node_waiting": false,
  "parent_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
  "node_waiting_input": 0
}
```

#### 【服务端→客户端】服务端输出客户端通知客户端输入

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858406963072811008,
  "type": "flow",
  "index": 0,
  "data": {
      "final": true,
      "id": null,
      "response_create_time": "2024-11-18 15:08",
      "response_create_time_timestamp": "1731913722548",
      "response_update_time": "2024-11-18 15:08",
      "response_update_time_timestamp": "1731913722548",
      "flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
      "history": [
          {
              "timestamp": 1731913531091,
              "time_date": "20241118150531091582",
              "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
              "robot_user_replying": "你好，欢迎您来我公司办理业务！现在将对您的姓名和联系方式进行收集。我们将会保密。请放心。",
              "robot_user_asking": "",
              "user_robot_replying": ""
          },
          {
              "timestamp": 1731913725910,
              "time_date": "20241118150845910619",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我叫张三"
          },
          {
              "timestamp": 1731913725910,
              "time_date": "20241118150845910619",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我叫张三"
          }
      ],
      "output": {
          "inputs": {},
          "variables": {},
          "entities": {
              "customerName": "张三"
          },
          "robot_user_asking": "请提供一下您的手机号",
          "user_robot_replying": "",
          "robot_user_replying": ""
      },
      "answer": "node_waiting_input",
      "content_type": "text",
      "session_id": "7edd9595-a57b-11ef-bce8-004238b4a21d",
      "flow_stage": "flow_running",
      "code": "002004",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0,
      "target_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
      "source_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
      "cur_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
      "parent_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748"
  },
  "finish": "y",
  "cur_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
  "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
  "debug": 1,
  "node_developer": 0,
  "flow_name": "客户信息收集小助手",
  "node_type": "parameter_extractor",
  "node_title": null,
  "node_waiting": true,
  "parent_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
  "node_waiting_input": 1
}
```

#### 【服务端→客户端】type为flow且finish为y表示当前轮次flow交互完成

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858406156273270784,
  "type": "flow",
  "index": 0,
  "data": {
      "code": "002005",
      "answer": "current_communication_complete",
      "content_type": "text",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0
  },
  "finish": "y"
}
```

### 第三轮

#### 【客户端→服务端】question为用户提供手机号信息，提供flow需要收集的信息

```json
{
  "question": "我的手机号是133103335027",
  "username": "me",
  "cybertron-robot-key": "PL1qFV************lhkA0NSPo=",
  "cybertron-robot-token": "MTcwMjg************************************ZdWJ3ckd1ODQ9",
  "segment_code": "cd0f14c3-801c-4b2d-8882-395e4ee7b9f5",
  "open_flow_trigger": "direct",
  "open_flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
  "open_flow_node_uuid": "",
  "open_flow_node_inputs": {},
  "open_flow_debug": 1
}
```

#### 【服务端→客户端】服务端收到报文

```json
{
  "code": "000000",
  "message": "task send success,",
  "type": "json",
  "index": -2,
  "data": {
    "task_id": "266cf5eea57c11efa06a004238b4a21d"
  }
}
```

```json
{
  "code": "000000",
  "message": "send question success",
  "index": -1,
  "type": "json",
  "data": {
    "question": "我的手机号是133103335027",
    "dialog_id": "1858407336739160064"
  }
}
```

#### 【服务端→客户端】服务端收集到的手机号实体信息

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858407336739160064,
  "type": "flow",
  "index": 0,
  "data": {
      "final": true,
      "id": null,
      "response_create_time": "2024-11-18 15:10",
      "response_create_time_timestamp": "1731913811598",
      "response_update_time": "2024-11-18 15:10",
      "response_update_time_timestamp": "1731913811598",
      "flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
      "history": [
          {
              "timestamp": 1731913531091,
              "time_date": "20241118150531091582",
              "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
              "robot_user_replying": "你好，欢迎您来我公司办理业务！现在将对您的姓名和联系方式进行收集。我们将会保密。请放心。",
              "robot_user_asking": "",
              "user_robot_replying": ""
          },
          {
              "timestamp": 1731913725910,
              "time_date": "20241118150845910619",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我叫张三"
          },
          {
              "timestamp": 1731913728170,
              "time_date": "20241118150848170189",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "请提供一下您的手机号",
              "user_robot_replying": ""
          },
          {
              "timestamp": 1731913814087,
              "time_date": "20241118151014087544",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我的手机号是133103335027"
          },
          {
              "timestamp": 1731913814087,
              "time_date": "20241118151014087544",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我的手机号是133103335027"
          }
      ],
      "output": {
          "inputs": {},
          "variables": {},
          "entities": {
              "customerName": "张三",
              "customerPhoneNumber": "133103335027"
          },
          "robot_user_asking": "",
          "user_robot_replying": "我的手机号是133103335027",
          "robot_user_replying": ""
      },
      "answer": "entity: customerPhoneNumber, value: 133103335027",
      "content_type": "text",
      "session_id": "7edd9595-a57b-11ef-bce8-004238b4a21d",
      "flow_stage": "flow_running",
      "code": "002003",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0,
      "target_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
      "source_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
      "cur_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
      "parent_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748"
  },
  "finish": "y",
  "cur_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
  "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
  "debug": 1,
  "node_developer": 0,
  "flow_name": "客户信息收集小助手",
  "node_type": "parameter_extractor",
  "node_title": null,
  "node_waiting": false,
  "parent_node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
  "node_waiting_input": 0
}
```

#### 【服务端→客户端】节点调转信息，从节点2跳转到节点4执行代码节点

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858407336739160064,
  "type": "flow",
  "index": 0,
  "data": {
      "final": true,
      "id": null,
      "response_create_time": "2024-11-18 15:10",
      "response_create_time_timestamp": "1731913811598",
      "response_update_time": "2024-11-18 15:10",
      "response_update_time_timestamp": "1731913811598",
      "flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
      "history": [
          {
              "timestamp": 1731913531091,
              "time_date": "20241118150531091582",
              "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
              "robot_user_replying": "你好，欢迎您来我公司办理业务！现在将对您的姓名和联系方式进行收集。我们将会保密。请放心。",
              "robot_user_asking": "",
              "user_robot_replying": ""
          },
          {
              "timestamp": 1731913725910,
              "time_date": "20241118150845910619",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我叫张三"
          },
          {
              "timestamp": 1731913728170,
              "time_date": "20241118150848170189",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "请提供一下您的手机号",
              "user_robot_replying": ""
          },
          {
              "timestamp": 1731913814087,
              "time_date": "20241118151014087544",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我的手机号是133103335027"
          },
          {
              "timestamp": 1731913814087,
              "time_date": "20241118151014087544",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我的手机号是133103335027"
          }
      ],
      "output": {
          "inputs": {},
          "variables": {},
          "entities": {
              "customerName": "张三",
              "customerPhoneNumber": "133103335027"
          },
          "robot_user_asking": "",
          "user_robot_replying": "",
          "robot_user_replying": ""
      },
      "answer": "node_id: 658d5db7-2460-4ed9-9842-59398e0b894a enter by prev_node_id: 67a4219c-963d-4e9d-8af9-81e6d3af6095",
      "content_type": "text",
      "session_id": "7edd9595-a57b-11ef-bce8-004238b4a21d",
      "flow_stage": "flow_running",
      "code": "002002",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0,
      "target_node_id": "658d5db7-2460-4ed9-9842-59398e0b894a",
      "source_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
      "cur_node_id": "658d5db7-2460-4ed9-9842-59398e0b894a",
      "parent_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095"
  },
  "finish": "y",
  "cur_node_id": "658d5db7-2460-4ed9-9842-59398e0b894a",
  "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
  "debug": 1,
  "node_developer": 0,
  "flow_name": "客户信息收集小助手",
  "node_type": "flow_code",
  "node_title": "",
  "node_waiting": false,
  "parent_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
  "node_waiting_input": 0
}
```

#### 【服务端→客户端】code代码节点输出调试信息

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858407336739160064,
  "type": "flow",
  "index": 0,
  "data": {
      "final": true,
      "id": null,
      "response_create_time": "2024-11-18 15:10",
      "response_create_time_timestamp": "1731913811598",
      "response_update_time": "2024-11-18 15:10",
      "response_update_time_timestamp": "1731913811598",
      "flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
      "history": [
          {
              "timestamp": 1731913531091,
              "time_date": "20241118150531091582",
              "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
              "robot_user_replying": "你好，欢迎您来我公司办理业务！现在将对您的姓名和联系方式进行收集。我们将会保密。请放心。",
              "robot_user_asking": "",
              "user_robot_replying": ""
          },
          {
              "timestamp": 1731913725910,
              "time_date": "20241118150845910619",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我叫张三"
          },
          {
              "timestamp": 1731913728170,
              "time_date": "20241118150848170189",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "请提供一下您的手机号",
              "user_robot_replying": ""
          },
          {
              "timestamp": 1731913814087,
              "time_date": "20241118151014087544",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我的手机号是133103335027"
          },
          {
              "timestamp": 1731913814087,
              "time_date": "20241118151014087544",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我的手机号是133103335027"
          }
      ],
      "output": {
          "inputs": {},
          "variables": {},
          "entities": {
              "customerName": "张三",
              "customerPhoneNumber": "133103335027"
          },
          "robot_user_asking": "",
          "user_robot_replying": "",
          "robot_user_replying": ""
      },
      "answer": "{\"my_var\": \"张 先生, 您的尾号是: 5027。\", \"last_code_time_cost\": 0.6902499198913574, \"last_code_error\": \"\"}",
      "content_type": "text",
      "session_id": "7edd9595-a57b-11ef-bce8-004238b4a21d",
      "flow_stage": "flow_running",
      "code": "002003",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0,
      "target_node_id": "658d5db7-2460-4ed9-9842-59398e0b894a",
      "source_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
      "cur_node_id": "658d5db7-2460-4ed9-9842-59398e0b894a",
      "parent_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095"
  },
  "finish": "y",
  "cur_node_id": "658d5db7-2460-4ed9-9842-59398e0b894a",
  "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
  "debug": 1,
  "node_developer": 0,
  "flow_name": "客户信息收集小助手",
  "node_type": "flow_code",
  "node_title": "",
  "node_waiting": false,
  "parent_node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
  "node_waiting_input": 0
}
```

#### 【服务端→客户端】flow节点跳转信息，从code节点跳转到文本回复节点

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858407336739160064,
  "type": "flow",
  "index": 0,
  "data": {
      "final": true,
      "id": null,
      "response_create_time": "2024-11-18 15:10",
      "response_create_time_timestamp": "1731913811598",
      "response_update_time": "2024-11-18 15:10",
      "response_update_time_timestamp": "1731913811598",
      "flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
      "history": [
          {
              "timestamp": 1731913531091,
              "time_date": "20241118150531091582",
              "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
              "robot_user_replying": "你好，欢迎您来我公司办理业务！现在将对您的姓名和联系方式进行收集。我们将会保密。请放心。",
              "robot_user_asking": "",
              "user_robot_replying": ""
          },
          {
              "timestamp": 1731913725910,
              "time_date": "20241118150845910619",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我叫张三"
          },
          {
              "timestamp": 1731913728170,
              "time_date": "20241118150848170189",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "请提供一下您的手机号",
              "user_robot_replying": ""
          },
          {
              "timestamp": 1731913814087,
              "time_date": "20241118151014087544",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我的手机号是133103335027"
          },
          {
              "timestamp": 1731913814087,
              "time_date": "20241118151014087544",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我的手机号是133103335027"
          }
      ],
      "output": {
          "inputs": {},
          "variables": {
              "last_code_response": {
                  "desc": "张 先生, 您的尾号是: 5027。"
              },
              "my_var": "张 先生, 您的尾号是: 5027。",
              "last_code_time_cost": 0.6902499198913574,
              "last_code_error": ""
          },
          "entities": {
              "customerName": "张三",
              "customerPhoneNumber": "133103335027"
          },
          "robot_user_asking": "",
          "user_robot_replying": "",
          "robot_user_replying": ""
      },
      "answer": "node_id: 8893d45d-64a4-4854-8f51-0c4c18842839 enter by prev_node_id: 658d5db7-2460-4ed9-9842-59398e0b894a",
      "content_type": "text",
      "session_id": "7edd9595-a57b-11ef-bce8-004238b4a21d",
      "flow_stage": "flow_running",
      "code": "002002",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0,
      "target_node_id": "8893d45d-64a4-4854-8f51-0c4c18842839",
      "source_node_id": "658d5db7-2460-4ed9-9842-59398e0b894a",
      "cur_node_id": "8893d45d-64a4-4854-8f51-0c4c18842839",
      "parent_node_id": "658d5db7-2460-4ed9-9842-59398e0b894a"
  },
  "finish": "y",
  "cur_node_id": "8893d45d-64a4-4854-8f51-0c4c18842839",
  "node_id": "658d5db7-2460-4ed9-9842-59398e0b894a",
  "debug": 1,
  "node_developer": 0,
  "flow_name": "客户信息收集小助手",
  "node_type": "answer",
  "node_title": null,
  "node_waiting": false,
  "parent_node_id": "658d5db7-2460-4ed9-9842-59398e0b894a",
  "node_waiting_input": 0
}
```

#### 【服务端→客户端】flow文本回复节点输出信息，见data.answer

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858407336739160064,
  "type": "flow",
  "index": 0,
  "data": {
      "final": true,
      "id": null,
      "response_create_time": "2024-11-18 15:10",
      "response_create_time_timestamp": "1731913811598",
      "response_update_time": "2024-11-18 15:10",
      "response_update_time_timestamp": "1731913811598",
      "flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
      "history": [
          {
              "timestamp": 1731913531091,
              "time_date": "20241118150531091582",
              "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
              "robot_user_replying": "你好，欢迎您来我公司办理业务！现在将对您的姓名和联系方式进行收集。我们将会保密。请放心。",
              "robot_user_asking": "",
              "user_robot_replying": ""
          },
          {
              "timestamp": 1731913725910,
              "time_date": "20241118150845910619",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我叫张三"
          },
          {
              "timestamp": 1731913728170,
              "time_date": "20241118150848170189",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "请提供一下您的手机号",
              "user_robot_replying": ""
          },
          {
              "timestamp": 1731913814087,
              "time_date": "20241118151014087544",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我的手机号是133103335027"
          },
          {
              "timestamp": 1731913814087,
              "time_date": "20241118151014087544",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我的手机号是133103335027"
          },
          {
              "timestamp": 1731913814943,
              "time_date": "20241118151014943252",
              "node_id": "8893d45d-64a4-4854-8f51-0c4c18842839",
              "robot_user_replying": "张 先生, 您的尾号是: 5027。信息收集完成。谢谢您的配合。",
              "robot_user_asking": "",
              "user_robot_replying": ""
          }
      ],
      "output": {
          "inputs": {},
          "variables": {
              "last_code_response": {
                  "desc": "张 先生, 您的尾号是: 5027。"
              },
              "my_var": "张 先生, 您的尾号是: 5027。",
              "last_code_time_cost": 0.6902499198913574,
              "last_code_error": ""
          },
          "entities": {
              "customerName": "张三",
              "customerPhoneNumber": "133103335027"
          },
          "robot_user_asking": "",
          "user_robot_replying": "",
          "robot_user_replying": "张 先生, 您的尾号是: 5027。信息收集完成。谢谢您的配合。"
      },
      "answer": "张 先生, 您的尾号是: 5027。信息收集完成。谢谢您的配合。",
      "content_type": "text",
      "session_id": "7edd9595-a57b-11ef-bce8-004238b4a21d",
      "flow_stage": "flow_running",
      "code": "000000",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0,
      "target_node_id": "8893d45d-64a4-4854-8f51-0c4c18842839",
      "source_node_id": "658d5db7-2460-4ed9-9842-59398e0b894a",
      "cur_node_id": "8893d45d-64a4-4854-8f51-0c4c18842839",
      "parent_node_id": "658d5db7-2460-4ed9-9842-59398e0b894a"
  },
  "finish": "y",
  "cur_node_id": "8893d45d-64a4-4854-8f51-0c4c18842839",
  "node_id": "658d5db7-2460-4ed9-9842-59398e0b894a",
  "debug": 1,
  "node_developer": 0,
  "flow_name": "客户信息收集小助手",
  "node_type": "answer",
  "node_title": null,
  "node_waiting": false,
  "parent_node_id": "658d5db7-2460-4ed9-9842-59398e0b894a",
  "node_waiting_input": 0
}
```

#### 【服务端→客户端】flow执行退出信息

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858407336739160064,
  "type": "flow",
  "index": 0,
  "data": {
      "final": true,
      "id": null,
      "response_create_time": "2024-11-18 15:10",
      "response_create_time_timestamp": "1731913811598",
      "response_update_time": "2024-11-18 15:10",
      "response_update_time_timestamp": "1731913811598",
      "flow_uuid": "3c61d330-a577-11ef-ad83-e4434b3011a0",
      "history": [
          {
              "timestamp": 1731913531091,
              "time_date": "20241118150531091582",
              "node_id": "a93311b9-7e1b-40d3-a0e5-94e26e2d9748",
              "robot_user_replying": "你好，欢迎您来我公司办理业务！现在将对您的姓名和联系方式进行收集。我们将会保密。请放心。",
              "robot_user_asking": "",
              "user_robot_replying": ""
          },
          {
              "timestamp": 1731913725910,
              "time_date": "20241118150845910619",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我叫张三"
          },
          {
              "timestamp": 1731913728170,
              "time_date": "20241118150848170189",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "请提供一下您的手机号",
              "user_robot_replying": ""
          },
          {
              "timestamp": 1731913814087,
              "time_date": "20241118151014087544",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我的手机号是133103335027"
          },
          {
              "timestamp": 1731913814087,
              "time_date": "20241118151014087544",
              "node_id": "67a4219c-963d-4e9d-8af9-81e6d3af6095",
              "robot_user_replying": "",
              "robot_user_asking": "",
              "user_robot_replying": "我的手机号是133103335027"
          },
          {
              "timestamp": 1731913814943,
              "time_date": "20241118151014943252",
              "node_id": "8893d45d-64a4-4854-8f51-0c4c18842839",
              "robot_user_replying": "张 先生, 您的尾号是: 5027。信息收集完成。谢谢您的配合。",
              "robot_user_asking": "",
              "user_robot_replying": ""
          }
      ],
      "output": {
          "inputs": {},
          "variables": {
              "last_code_response": {
                  "desc": "张 先生, 您的尾号是: 5027。"
              },
              "my_var": "张 先生, 您的尾号是: 5027。",
              "last_code_time_cost": 0.6902499198913574,
              "last_code_error": ""
          },
          "entities": {
              "customerName": "张三",
              "customerPhoneNumber": "133103335027"
          }
      },
      "answer": "flow_exit",
      "content_type": "text",
      "session_id": "7edd9595-a57b-11ef-bce8-004238b4a21d",
      "flow_stage": "flow_exit",
      "code": "002001",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0,
      "target_node_id": "8893d45d-64a4-4854-8f51-0c4c18842839",
      "source_node_id": "8893d45d-64a4-4854-8f51-0c4c18842839",
      "cur_node_id": "8893d45d-64a4-4854-8f51-0c4c18842839",
      "parent_node_id": "8893d45d-64a4-4854-8f51-0c4c18842839"
  },
  "finish": "y",
  "cur_node_id": "8893d45d-64a4-4854-8f51-0c4c18842839",
  "node_id": "8893d45d-64a4-4854-8f51-0c4c18842839",
  "debug": 1,
  "node_developer": 0,
  "flow_name": "客户信息收集小助手",
  "node_type": "answer",
  "node_title": null,
  "parent_node_id": "8893d45d-64a4-4854-8f51-0c4c18842839",
  "node_waiting_input": 0
}
```

#### 【服务端→客户端】flow当前轮次执行完成退出

```json
{
  "code": "000000",
  "message": "success",
  "dialog_id": 1858406156273270784,
  "type": "flow",
  "index": 0,
  "data": {
      "code": "002005",
      "answer": "current_communication_complete",
      "content_type": "text",
      "node_stream": 0,
      "node_answer_finish": "y",
      "node_answer_index": 0
  },
  "finish": "y"
}
```
