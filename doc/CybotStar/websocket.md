# WebSocket 接口

## 1. 接口概述

- **接口名称**：机器人对话-websocket接口
- **版本号**：v2.0.0

## 2. 基本能力

- 智能体多轮对话
- 智能体多轮持续对话

## 3. 接口信息

- **接口URL**：wss://www.cybotstar.cn/openapi/v2/ws/dialog
- **连接方式**：WebSocket

## 4. 协议描述

### 4.1 连接与心跳（非必须）

参考下述例子

### 4.2 发送聊天消息

**消息类型**：object

**字段说明（共14个）**：

| 参数名称 | 类型 | 是否必填 | 描述 |
|---------|------|---------|------|
| cybertron-robot-key | string | 是 | 智能体key |
| cybertron-robot-token | string | 是 | 智能体token |
| username | string | 是 | 赛博坦用户账号 |
| extra-header | string | 是 | 同http接口 |
| extra-body | string | 是 | 同http接口 |
| segment_code | string | 是 | 会话编码，用于切分对话历史 |
| question | string | 是 | 输入问题 |
| message_params | array | 否 | 传入模型的message字段，同openai的接口message |
| chat_history | array | 否 | 对话历史，拼到message中 |
| tip_message_extra | string | 否 | cybotstar机器人配置角色设定字段，接口调用时如果有该字段，本轮对话则覆盖cybotstar内机器人配置角色设定 |
| tip_message_params | object | 否 | 传入cybotstar机器人agent变量，支持配置为角色设定中的参数，也支持在绑定的对话流中应用。注意：同一个segment_code仅生效第一次传入值，且对话流中如对agent变量做赋值，则变量值会更新 |
| model_params | object | 否 | 模型参数，目前支持4个参数配置 |
| welcome | string | 否 | 当需要获取欢迎语时才传此字段，内容随便传非空即可，后面返回时读取agent配置的欢迎语。返回的answer有"wellcome"前缀，需客户端自行处理 |

#### 发送消息例：

**message_params**
```json
[
    {
        "role": "system",
        "content": "你是一个客服，能够回答很多问题"
    },
    {
        "role": "user",
        "content": "问题"
    }
]
```

**chat_history**
```json
[
    {
        "question": "北京有多大",
        "answer": "北京很大，7777"
    },
    {
        "question": "北京有多小",
        "answer": "北京很小，000000000000"
    }
]
```

**tip_message_extra**
```json
"你的名字是JoJo，你在。。。"
```

**tip_message_params**
```json
{
    "name": "孫悟空。。。"
}
```

**model_params**
```json
{
    "top_p": 0.98,
    "temperature": 0.1,
    "frequency_penalty": 1,
    "presence_penalty": 2,
    "max_tokens": 1000  // since: v1.4.10
}
```

#### 扩展字段备注说明：

- message_params、tip_message_extra、tip_message_params 这三个字段互斥，只有一个生效，参数优先级：message_params > tip_message_extra > tip_message_params，如果同时传入则优先级高的生效
- chat_history和message_params参数互斥，和tip_message_extra、tip_message_params、model_params可以一起传，按cybotstar规则拼到prompt中，可以使用cybotstar中的联网搜索和rag功能

### 4.3 接收聊天消息

**消息类型**：object

**字段说明（共6个）**：

| 字段名称 | 类型 | 描述 |
|---------|------|------|
| code | string | 响应状态码（6位数字），000000为正常，400开头为错误 |
| message | string | 状态描述文本，说明当前响应结果 |
| index | int | -4为引用图片；-3为联网搜索结果；-2为线程信息，可忽略；为-1时表示提问已确认收到；为0开始递增并且finish为n时，data为流式输出的回复片段，后续还有其他片段等待接收 |
| finish | string | 为y时，表示回复已结束；此时data中为所有的完整回复，用户根据需求决定取舍 |
| type | string | heartbeat心跳请求；string时data为可以直接拼接的文本；json时data内为一json序列化后的字符串；flow时data特殊解析规则下述 |
| data | string/object | 消息体，格式由type字段决定 |

### 4.4 响应体中 type=flow 时，data 内特殊结构

当type为flow时，data内字段如下：

| 字段名称 | 类型 | 描述 |
|---------|------|------|
| answer | string | 对话流返回的回答，使用这个客户端展示（重点字段） |
| content_type | string | 返回答案类型 |
| code | string | "000000"为正常，其他异常 |
| node_stream | int | 该节点是否流式输出；1：流式，0：非流式（一般涉及模型回复的会流式输出）（重点字段） |
| node_answer_index | string | 节点流式返回编号（重点字段） |
| node_answer_finish | string | 节点是否回答完成（重点字段） |
| flow_stage | string | flow_running |
| flow_uuid | string | 对话uuid，一般客户端用不到 |
| history | array | 对话流保存对话历史，一般客户端用不到 |
| output | array | 节点输出信息，一般客户端用不到 |
| target_node_id | string | 目标节点，一般客户端用不到 |
| source_node_id | string | 源节点，一般客户端用不到 |
| cur_node_id | string | 当前节点，一般客户端用不到 |
| node_id | string | 节点id，一般客户端用不到 |
| session_id | string | 会话id，一般客户端用不到 |

## 5. 示例

### 示例1-心跳（非必须）

**请求**
```json
{
    "type": "heartbeat",
    "data": "ping"
}
```

**响应**
```json
{
    "code": "000000",
    "message": "success",
    "type": "heartbeat",
    "data": "pong"
}
```

### 示例2-普通对话

**请求**
```json
{
    "cybertron-robot-key": "PL1qFV************lhkA0NSPo=",
    "cybertron-robot-token": "MTcwMjg3************************************dWJ3ckd1ODQ9",
    "username": "testuser",
    "question": "the weather info of beijing",
    "segment_code": "wWkE3bNkL3SzJX9orcO9P"
}
```

**响应**
```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1745062364369207296",
    "type": "string",
    "index": 0,
    "data": "在",
    "finish": "n"
}
```

```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1745062364369207296",
    "type": "string",
    "index": 1,
    "data": "茫",
    "finish": "n"
}
```

```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1745062364369207296",
    "type": "string",
    "index": 2,
    "data": "茫",
    "finish": "n"
}
```

// ...

```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1745062364369207296",
    "type": "string",
    "index": 219,
    "data": "。",
    "finish": "n"
}
```

```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1745062364369207296",
    "type": "json",
    "index": 220,
    "data": {
        "type": "string",
        "answer": "在茫茫人海中追寻，\n寻觅着心灵的归宿。\n阳光洒落在大地上，\n温暖了每一个角落。\n\n春风拂过花开处，\n芬芳弥漫在空气中。\n夏日炎炎阳光烈，\n沐浴在海边的沙滩。\n\n秋天的枫叶红如火，\n飘落在湖面的波涛。\n冬日的雪花纷飞舞，\n铺满了整个山脊高。\n\n人生如诗意绵长，\n每一行都值得珍藏。\n用心感受生活的美，\n让心灵在诗中飞翔。\n\n愿诗歌的力量陪伴，\n温暖每一个寂寞的夜晚。\n让文字的韵律跳跃，\n唤醒心中沉睡的梦想。\n\n这是我为你编织的诗，\n希望能给你带来一丝欢喜。\n让我们一起走进诗的世界，\n感受其中的美妙与自由。"
    },
    "finish": "y"
}
```

### 示例3-流式输出1

**请求**
```json
{
    "question": "",
    "username": "kKp5xVJJ_Gx6ZG2vJSvkQ",
    "cybertron-robot-key": "St8EsOt************xE9bMeKc=",
    "cybertron-robot-token": "MTczMDA5************************************M2VMR3JJc1U9",
    "segment_code": "_wg7RYDBFtarIHV_b0an6"
}
```

**响应**
```json
{
    "code": "400000",
    "message": "task send success,",
    "type": "json",
    "index": -2,
    "data": {
        "task_id": "df60fc1a94fa11ef993fe4434b3011a0"
    }
}
```

```json
{
    "code": "400000",
    "message": "send question success",
    "index": -1,
    "type": "json",
    "data": {
        "question": "",
        "dialog_id": "1850795752799076352",
        "id": "198778",
        "response_create_time": "2024-10-28 15:04",
        "response_create_time_timestamp": "1730099068247",
        "response_update_time": "2024-10-28 15:04",
        "response_update_time_timestamp": "1730099068247"
    }
}
```

```json
{
    "flow_redis_key": "215kKp5xVJJ_Gx6ZG2vJSvkQ_wg7RYDBFtarIHV_b0an2",
    "code": "000000",
    "message": "success",
    "dialog_id": 1850795752799076352,
    "type": "flow",
    "index": 0,
    "data": {
        "final": true,
        "id": null,
        "response_create_time": "2024-10-28 15:04",
        "response_create_time_timestamp": "1730099068259",
        "response_update_time": "2024-10-28 15:04",
        "response_update_time_timestamp": "1730099068259",
        "flow_uuid": "bfa90c48-91d8-11ef-8a93-e4434b3011a0",
        "history": [
            {
                "timestamp": 1730099068796,
                "time_date": "20241028150428796289",
                "node_id": "24e67700-ae1d-4f03-b5ef-609a23e0c7e5",
                "robot_user_replying": "请输入你要查询的游戏",
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
            "robot_user_replying": "请输入你要查询的游戏"
        },
        "answer": "请输入你要查询的游戏",
        "content_type": "text",
        "session_id": "df672ee6-94fa-11ef-8430-e4434b3011a0",
        "flow_stage": "flow_running",
        "code": "000000",
        "node_stream": 0,
        "target_node_id": "24e67700-ae1d-4f03-b5ef-609a23e0c7e5",
        "source_node_id": "24e67700-ae1d-4f03-b5ef-609a23e0c7e5",
        "cur_node_id": "24e67700-ae1d-4f03-b5ef-609a23e0c7e5",
        "node_id": "24e67700-ae1d-4f03-b5ef-609a23e0c7e5"
    },
    "finish": "y",
    "cur_node_id": "24e67700-ae1d-4f03-b5ef-609a23e0c7e5",
    "node_id": "24e67700-ae1d-4f03-b5ef-609a23e0c7e5",
    "debug": 0,
    "node_developer": 0,
    "flow_name": "游戏助手11",
    "node_type": "answer",
    "node_title": null
}
```

### 示例4-流式输出2

**请求**
```json
{
    "question": "原神",
    "username": "kKp5xVJJ_Gx6ZG2vJSvkQ",
    "cybertron-robot-key": "St8EsOt************xE9bMeKc=",
    "cybertron-robot-token": "MTczMDA5************************************M2VMR3JJc1U9",
    "segment_code": "_wg7RYDBFtarIHV_b0an6"
}
```

**响应**
```json
{
    "code": "400000",
    "message": "task send success,",
    "type": "json",
    "index": -2,
    "data": {
        "task_id": "ae3ccb1894fb11ef9f3de4434b3011a0"
    }
}
```

```json
{
    "code": "400000",
    "message": "send question success",
    "index": -1,
    "type": "json",
    "data": {
        "question": "原神",
        "dialog_id": "1850797208411308032",
        "id": "198790",
        "response_create_time": "2024-10-28 15:10",
        "response_create_time_timestamp": "1730099415291",
        "response_update_time": "2024-10-28 15:10",
        "response_update_time_timestamp": "1730099415291"
    }
}
```

```json
{
    "flow_redis_key": "215kKp5xVJJ_Gx6ZG2vJSvkQ_wg7RYDBFtarIHV_b0an2",
    "code": "000000",
    "message": "success",
    "dialog_id": 1850797208411308032,
    "type": "flow",
    "index": 0,
    "data": {
        "final": true,
        "id": null,
        "response_create_time": "2024-10-28 15:10",
        "response_create_time_timestamp": "1730099415379",
        "response_update_time": "2024-10-28 15:10",
        "response_update_time_timestamp": "1730099415379",
        "flow_uuid": "bfa90c48-91d8-11ef-8a93-e4434b3011a0",
        "history": [
            {
                "timestamp": 1730099070616,
                "time_date": "20241028150430616346",
                "node_id": "24e67700-ae1d-4f03-b5ef-609a23e0c7e5",
                "robot_user_replying": "请输入你要查询的游戏",
                "robot_user_asking": "",
                "user_robot_replying": ""
            },
            {
                "timestamp": 1730099320084,
                "time_date": "20241028150840084725",
                "node_id": "883cc5b6-55d2-4cb8-be94-de71fd6b93eb",
                "robot_user_replying": "",
                "robot_user_asking": "",
                "user_robot_replying": "原神"
            }
        ],
        "output": {
            "inputs": {},
            "variables": {
                "play_name": "原神"
            },
            "entities": {},
            "robot_user_asking": "",
            "user_robot_replying": "原神",
            "robot_user_replying": ""
        },
        "answer": "探索",
        "content_type": "text",
        "session_id": "df672ee6-94fa-11ef-8430-e4434b3011a0",
        "flow_stage": "flow_running",
        "code": "000000",
        "node_stream": 1,
        "target_node_id": "4b86d96a-9648-40ce-bcd3-b6dfaed132c6",
        "source_node_id": "883cc5b6-55d2-4cb8-be94-de71fd6b93eb",
        "cur_node_id": "4b86d96a-9648-40ce-bcd3-b6dfaed132c6",
        "node_id": "883cc5b6-55d2-4cb8-be94-de71fd6b93eb",
        "node_answer_index": 0,
        "node_answer_finish": "n"
    },
    "finish": "y",
    "cur_node_id": "4b86d96a-9648-40ce-bcd3-b6dfaed132c6",
    "node_id": "883cc5b6-55d2-4cb8-be94-de71fd6b93eb",
    "debug": 0,
    "node_developer": 0,
    "flow_name": "游戏助手11",
    "node_type": "llm",
    "node_title": null
}
```

继续接收流式数据片段...

### 示例5-插件-markdown

**请求**
```json
{
    "question": "the weather info of beijing",
    "username": "testuser",
    "cybertron-robot-key": "PL1qFV************lhkA0NSPo=",
    "cybertron-robot-token": "MTcwMjg3************************************dWJ3ckd1ODQ9",
    "segment_code": "wWkE3bNkL3SzJX9orcO9P"
}
```

**响应**
```json
{
    "code": "000000",
    "message": "send question success",
    "index": -1,
    "type": "json",
    "data": {
        "question": "the weather info of beijing",
        "dialog_id": "1745083757851394048"
    }
}
```

```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1745083757851394048",
    "type": "json",
    "index": 0,
    "data": {
        "type": "json",
        "answer": {
            "type": "markdown",
            "data": "| Weather               | Details            |\n|:----------------------|:-------------------|\n| Daytime weather       | Clear              |\n| Nighttime weather     | Clear              |\n| Daytime temperature   | 5 degrees Celsius  |\n| Nighttime temperature | -7 degrees Celsius |\n| Wind force            | 1-3 level          |\n| Date                  | 2024-01-10         |",
            "raw_data": {
                "raw": {
                    "Daytime weather": "Clear",
                    "Nighttime weather": "Clear",
                    "Daytime temperature": "5 degrees Celsius",
                    "Nighttime temperature": "-7 degrees Celsius",
                    "Wind force": "1-3 level",
                    "Date": "2024-01-10"
                },
                "markdown": "| Weather               | Details            |\n|:----------------------|:-------------------|\n| Daytime weather       | Clear              |\n| Nighttime weather     | Clear              |\n| Daytime temperature   | 5 degrees Celsius  |\n| Nighttime temperature | -7 degrees Celsius |\n| Wind force            | 1-3 level          |\n| Date                  | 2024-01-10         |",
                "type": "markdown",
                "desc": "According to the weather forecast, 2024-01-10, Beijing, the weather conditions are as follows: \n- Daytime weather: Clear\n- Nighttime weather: Clear\n- Daytime temperature: 5 degrees Celsius\n- Nighttime temperature: -7 degrees Celsius\n- Wind force: 1-3 level\n\nPlease plan your activities accordingly.  Pay attention to add clothes when necessary. If you have any other questions, I'm here to assist you."
            },
            "description": "According to the weather forecast, 2024-01-10, Beijing, the weather conditions are as follows: \n- Daytime weather: Clear\n- Nighttime weather: Clear\n- Daytime temperature: 5 degrees Celsius\n- Nighttime temperature: -7 degrees Celsius\n- Wind force: 1-3 level\n\nPlease plan your activities accordingly.  Pay attention to add clothes when necessary. If you have any other questions, I'm here to assist you."
        }
    },
    "finish": "n"
}
```

```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1745083757851394048",
    "type": "json",
    "index": 1,
    "data": {
        "type": "json",
        "answer": {
            "type": "markdown",
            "data": "| Weather               | Details            |\n|:----------------------|:-------------------|\n| Daytime weather       | Clear              |\n| Nighttime weather     | Clear              |\n| Daytime temperature   | 5 degrees Celsius  |\n| Nighttime temperature | -7 degrees Celsius |\n| Wind force            | 1-3 level          |\n| Date                  | 2024-01-10         |",
            "raw_data": {
                "raw": {
                    "Daytime weather": "Clear",
                    "Nighttime weather": "Clear",
                    "Daytime temperature": "5 degrees Celsius",
                    "Nighttime temperature": "-7 degrees Celsius",
                    "Wind force": "1-3 level",
                    "Date": "2024-01-10"
                },
                "markdown": "| Weather               | Details            |\n|:----------------------|:-------------------|\n| Daytime weather       | Clear              |\n| Nighttime weather     | Clear              |\n| Daytime temperature   | 5 degrees Celsius  |\n| Nighttime temperature | -7 degrees Celsius |\n| Wind force            | 1-3 level          |\n| Date                  | 2024-01-10         |",
                "type": "markdown",
                "desc": "According to the weather forecast, 2024-01-10, Beijing, the weather conditions are as follows: \n- Daytime weather: Clear\n- Nighttime weather: Clear\n- Daytime temperature: 5 degrees Celsius\n- Nighttime temperature: -7 degrees Celsius\n- Wind force: 1-3 level\n\nPlease plan your activities accordingly.  Pay attention to add clothes when necessary. If you have any other questions, I'm here to assist you."
            },
            "description": "According to the weather forecast, 2024-01-10, Beijing, the weather conditions are as follows: \n- Daytime weather: Clear\n- Nighttime weather: Clear\n- Daytime temperature: 5 degrees Celsius\n- Nighttime temperature: -7 degrees Celsius\n- Wind force: 1-3 level\n\nPlease plan your activities accordingly.  Pay attention to add clothes when necessary. If you have any other questions, I'm here to assist you."
        }
    },
    "finish": "y"
}
```

### 示例6-插件-chart

**请求**
```json
{
    "question": "公仔公司网站用户数据详情",
    "username": "testuser",
    "cybertron-robot-key": "PL1qFV************lhkA0NSPo=",
    "cybertron-robot-token": "MTcwMjg3************************************dWJ3ckd1ODQ9",
    "segment_code": "wWkE3bNkL3SzJX9orcO9P"
}
```

**响应**
```json
{
    "code": "000000",
    "message": "send question success",
    "index": -1,
    "type": "json",
    "data": {
        "question": "公仔公司网站用户数据详情",
        "dialog_id": "1745085048405180416"
    }
}
```

```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1745085048405180416",
    "type": "json",
    "index": 0,
    "data": {
        "type": "json",
        "answer": {
            "type": "markdown",
            "data": "| 日期       |   注册用户 |   付费用户 |\n|:-----------|-----------:|-----------:|\n| 2024-01-10 |        733 |        220 |\n| 2024-01-09 |        882 |        414 |\n| 2024-01-08 |        183 |        102 |\n| 2024-01-07 |        156 |        139 |\n| 2024-01-06 |        723 |        432 |\n| 2024-01-05 |        769 |        645 |\n| 2024-01-04 |        508 |        360 |",
            "raw_data": {
                "data": [
                    {
                        "日期": "2024-01-04",
                        "注册用户": 508,
                        "付费用户": 360
                    },
                    {
                        "日期": "2024-01-05",
                        "注册用户": 769,
                        "付费用户": 645
                    },
                    {
                        "日期": "2024-01-06",
                        "注册用户": 723,
                        "付费用户": 432
                    },
                    {
                        "日期": "2024-01-07",
                        "注册用户": 156,
                        "付费用户": 139
                    },
                    {
                        "日期": "2024-01-08",
                        "注册用户": 183,
                        "付费用户": 102
                    },
                    {
                        "日期": "2024-01-09",
                        "注册用户": 882,
                        "付费用户": 414
                    },
                    {
                        "日期": "2024-01-10",
                        "注册用户": 733,
                        "付费用户": 220
                    }
                ],
                "raw": [
                    {
                        "日期": "2024-01-04",
                        "注册用户": 508,
                        "付费用户": 360
                    },
                    {
                        "日期": "2024-01-05",
                        "注册用户": 769,
                        "付费用户": 645
                    },
                    {
                        "日期": "2024-01-06",
                        "注册用户": 723,
                        "付费用户": 432
                    },
                    {
                        "日期": "2024-01-07",
                        "注册用户": 156,
                        "付费用户": 139
                    },
                    {
                        "日期": "2024-01-08",
                        "注册用户": 183,
                        "付费用户": 102
                    },
                    {
                        "日期": "2024-01-09",
                        "注册用户": 882,
                        "付费用户": 414
                    },
                    {
                        "日期": "2024-01-10",
                        "注册用户": 733,
                        "付费用户": 220
                    }
                ],
                "markdown": "| 日期       |   注册用户 |   付费用户 |\n|:-----------|-----------:|-----------:|\n| 2024-01-10 |        733 |        220 |\n| 2024-01-09 |        882 |        414 |\n| 2024-01-08 |        183 |        102 |\n| 2024-01-07 |        156 |        139 |\n| 2024-01-06 |        723 |        432 |\n| 2024-01-05 |        769 |        645 |\n| 2024-01-04 |        508 |        360 |",
                "field_headers": [
                    "日期",
                    "注册用户",
                    "付费用户"
                ],
                "chart_type": "bar",
                "type": "chart",
                "dimension": "日期",
                "desc": "根据查询近三天注册用户数分别为733、882、183，付费用户数分别为220、414、102。如果还有其他问题，我可以继续为您提供帮助。"
            },
            "description": "根据查询近三天注册用户数分别为733、882、183，付费用户数分别为220、414、102。如果还有其他问题，我可以继续为您提供帮助。"
        }
    },
    "finish": "n"
}
```

```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1745085048405180416",
    "type": "json",
    "index": 1,
    "data": {
        "type": "json",
        "answer": {
            "type": "markdown",
            "data": "| 日期       |   注册用户 |   付费用户 |\n|:-----------|-----------:|-----------:|\n| 2024-01-10 |        733 |        220 |\n| 2024-01-09 |        882 |        414 |\n| 2024-01-08 |        183 |        102 |\n| 2024-01-07 |        156 |        139 |\n| 2024-01-06 |        723 |        432 |\n| 2024-01-05 |        769 |        645 |\n| 2024-01-04 |        508 |        360 |",
            "raw_data": {
                "data": [
                    {
                        "日期": "2024-01-04",
                        "注册用户": 508,
                        "付费用户": 360
                    },
                    {
                        "日期": "2024-01-05",
                        "注册用户": 769,
                        "付费用户": 645
                    },
                    {
                        "日期": "2024-01-06",
                        "注册用户": 723,
                        "付费用户": 432
                    },
                    {
                        "日期": "2024-01-07",
                        "注册用户": 156,
                        "付费用户": 139
                    },
                    {
                        "日期": "2024-01-08",
                        "注册用户": 183,
                        "付费用户": 102
                    },
                    {
                        "日期": "2024-01-09",
                        "注册用户": 882,
                        "付费用户": 414
                    },
                    {
                        "日期": "2024-01-10",
                        "注册用户": 733,
                        "付费用户": 220
                    }
                ],
                "raw": [
                    {
                        "日期": "2024-01-04",
                        "注册用户": 508,
                        "付费用户": 360
                    },
                    {
                        "日期": "2024-01-05",
                        "注册用户": 769,
                        "付费用户": 645
                    },
                    {
                        "日期": "2024-01-06",
                        "注册用户": 723,
                        "付费用户": 432
                    },
                    {
                        "日期": "2024-01-07",
                        "注册用户": 156,
                        "付费用户": 139
                    },
                    {
                        "日期": "2024-01-08",
                        "注册用户": 183,
                        "付费用户": 102
                    },
                    {
                        "日期": "2024-01-09",
                        "注册用户": 882,
                        "付费用户": 414
                    },
                    {
                        "日期": "2024-01-10",
                        "注册用户": 733,
                        "付费用户": 220
                    }
                ],
                "markdown": "| 日期       |   注册用户 |   付费用户 |\n|:-----------|-----------:|-----------:|\n| 2024-01-10 |        733 |        220 |\n| 2024-01-09 |        882 |        414 |\n| 2024-01-08 |        183 |        102 |\n| 2024-01-07 |        156 |        139 |\n| 2024-01-06 |        723 |        432 |\n| 2024-01-05 |        769 |        645 |\n| 2024-01-04 |        508 |        360 |",
                "field_headers": [
                    "日期",
                    "注册用户",
                    "付费用户"
                ],
                "chart_type": "bar",
                "type": "chart",
                "dimension": "日期",
                "desc": "根据查询近三天注册用户数分别为733、882、183，付费用户数分别为220、414、102。如果还有其他问题，我可以继续为您提供帮助。"
            },
            "description": "根据查询近三天注册用户数分别为733、882、183，付费用户数分别为220、414、102。如果还有其他问题，我可以继续为您提供帮助。"
        }
    },
    "finish": "y"
}
```

### 示例7-插件-联网搜索，知识库图片（since：v1.4.0）

**请求**
```json
{
    "question": "北京最新消息，北京在哪里",
    "username": "me@host.com",
    "cybertron-robot-key": "dlaJconh*************iZCR4w%3D",
    "cybertron-robot-token": "MTcxMjEwO********************************klBMmFpUFZ0T1U9",
    "segment_code": "-rohx1iD39tOpWeJF3vUy"
}
```

**响应**
```json
{
    "code": "000000",
    "message": "task send success,",
    "type": "json",
    "index": -2,
    "data": {
        "task_id": "520202d82af911ef8ddc2800af4a2ec9"
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
        "question": "北京最新消息，北京在哪里",
        "dialog_id": "1801909085148839936"
    }
}
```

```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1801909085148839936",
    "type": "online_search",
    "index": -3,
    "data": {
        "index": 0,
        "type": "online_search",
        "answer": {
            "title": "新华网北京频道_北京新闻_首都",
            "href": "http://www.bj.xinhuanet.com/",
            "body": "新华社记者看北京 更多. \"南水\"入京达100亿立方米. 北京严厉打击违法违规\"自媒体\"账号9000余个. 北京：高温下坚守岗位. 北京将对高温作业用工开展联合检查. \"中医药+AI\" 清华大学李梢团队获日内瓦国际发明展最高奖. 端午节假期北京接待游客779.2万人次 ..."
        },
        "online_search_info": {
            "avatar": "api/media/file/image/avatar/online_search.png",
            "plugin_name": "联网搜索",
            "name": "text_search",
            "status": "success"
        }
    },
    "finish": "n"
}
```

```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1801909085148839936",
    "type": "online_search",
    "index": -3,
    "data": {
        "index": 1,
        "type": "online_search",
        "answer": {
            "title": "北京新冠肺炎最新消息_北京本土病例|风险等级|核酸检测|隔离-北京本地宝",
            "href": "http://m.bj.bendibao.com/news/xinxingfeiyan/",
            "body": "有时在家家福生活超市（马家楼店）购物、在住所附近晋汉子面食馆用餐。 1月19日晨在厨元家服务部购买食品，中午、晚间均在家家福生活超市（马家楼店）购物，返家后未再外出。同日报告结果为阴性。 1月22日报告结果为阴性。 1月23日报告结果为阳性。"
        },
        "online_search_info": {
            "avatar": "api/media/file/image/avatar/online_search.png",
            "plugin_name": "联网搜索",
            "name": "text_search",
            "status": "success"
        }
    },
    "finish": "n"
}
```

```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1801909085148839936",
    "type": "online_search",
    "index": -3,
    "data": {
        "index": 2,
        "type": "online_search",
        "answer": {
            "title": "新华网北京频道_北京新闻_首都",
            "href": "http://bj.news.cn/",
            "body": "北京要闻 更多. 端午假期北京重点商圈客流量超2000万人次. 端午节假期北京市属公园迎客124万人次. 北京：颐和园天坛动物园如何成\"顶流\". 北京：高温橙色预警发布 明天冲38℃. 1毫米精准控制造就机器人巧\"手\". 114预约挂号平台覆盖290家医院. 北京：去年 ..."
        },
        "online_search_info": {
            "avatar": "api/media/file/image/avatar/online_search.png",
            "plugin_name": "联网搜索",
            "name": "text_search",
            "status": "success"
        }
    },
    "finish": "n"
}
```

```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1801909085148839936",
    "type": "string",
    "index": 0,
    "data": "北京",
    "finish": "n"
}
```

```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1801909085148839936",
    "type": "string",
    "index": 1,
    "data": "是",
    "finish": "n"
}
```

...（继续流式输出字符，直到 index 66）...

```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1801909085148839936",
    "type": "string",
    "index": 66,
    "data": "。",
    "finish": "n"
}
```

```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1801909085148839936",
    "type": "images",
    "index": -4,
    "data": {
        "index": 0,
        "type": "images",
        "answer": "https://192.168.160.58/api/app/cybertron/knowledge_file/image/knowledge/qa_img/1718088783776a6e19-8cb2-4551-b3a5-7500e723f24d北京2.jpg"
    },
    "finish": "n"
}
```

```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1801909085148839936",
    "type": "images",
    "index": -4,
    "data": {
        "index": 1,
        "type": "images",
        "answer": "https://192.168.160.58/api/app/cybertron/knowledge_file/image/knowledge/qa_img/17180887862cd0cce0-937f-49c0-bf93-89775d53c1bf北京1.jpeg"
    },
    "finish": "n"
}
```

```json
{
    "code": "000000",
    "message": "success",
    "dialog_id": "1801909085148839936",
    "type": "json",
    "index": 67,
    "data": {
        "type": "json",
        "answer": {
            "type": "online_search|image",
            "online_search": [
                {
                    "title": "新华网北京频道_北京新闻_首都",
                    "href": "http://www.bj.xinhuanet.com/",
                    "body": "新华社记者看北京 更多. \"南水\"入京达100亿立方米. 北京严厉打击违法违规\"自媒体\"账号9000余个. 北京：高温下坚守岗位. 北京将对高温作业用工开展联合检查. \"中医药+AI\" 清华大学李梢团队获日内瓦国际发明展最高奖. 端午节假期北京接待游客779.2万人次 ..."
                },
                {
                    "title": "北京新冠肺炎最新消息_北京本土病例|风险等级|核酸检测|隔离-北京本地宝",
                    "href": "http://m.bj.bendibao.com/news/xinxingfeiyan/",
                    "body": "有时在家家福生活超市（马家楼店）购物、在住所附近晋汉子面食馆用餐。 1月19日晨在厨元家服务部购买食品，中午、晚间均在家家福生活超市（马家楼店）购物，返家后未再外出。同日报告结果为阴性。 1月22日报告结果为阴性。 1月23日报告结果为阳性。"
                },
                {
                    "title": "新华网北京频道_北京新闻_首都",
                    "href": "http://bj.news.cn/",
                    "body": "北京要闻 更多. 端午假期北京重点商圈客流量超2000万人次. 端午节假期北京市属公园迎客124万人次. 北京：颐和园天坛动物园如何成\"顶流\". 北京：高温橙色预警发布 明天冲38℃. 1毫米精准控制造就机器人巧\"手\". 114预约挂号平台覆盖290家医院. 北京：去年 ..."
                }
            ],
            "description": "北京是中国的首都，位于华北地区，地理坐标为东经116.4074度、北纬39.9042度。如需了解北京的最新消息，请您浏览相关新闻网站或使用新闻类移动应用获取最新信息。",
            "image": [
                "https://192.168.160.58/api/app/cybertron/knowledge_file/image/knowledge/qa_img/1718088783776a6e19-8cb2-4551-b3a5-7500e723f24d北京2.jpg",
                "https://192.168.160.58/api/app/cybertron/knowledge_file/image/knowledge/qa_img/17180887862cd0cce0-937f-49c0-bf93-89775d53c1bf北京1.jpeg"
            ],
            "online_search_info": {
                "avatar": "api/media/file/image/avatar/online_search.png",
                "plugin_name": "联网搜索",
                "name": "text_search",
                "status": "success"
            }
        }
    },
    "finish": "y"
}
```
