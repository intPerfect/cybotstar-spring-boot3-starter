# 获取智能体会话历史

获取智能体会话历史

## API 端点

**方法**: `POST`

**URL**: `https://www.cybotstar.cn/openapi/v1/conversation/segment/get_list/`

## 请求示例

```bash
curl --request POST \
  --url https://www.cybotstar.cn/openapi/v1/conversation/segment/get_list/ \
  --header 'Content-Type: application/json' \
  --header 'cybertron-robot-key: <api-key>' \
  --header 'cybertron-robot-token: <api-key>' \
  --header 'username: <api-key>' \
  --data '{
  "username": "<string>",
  "filter_mode": 0,
  "filter_user_code": "<string>",
  "create_start_time": "2023-11-07T05:31:56Z",
  "create_end_time": "2023-11-07T05:31:56Z",
  "message_source": "<string>",
  "segment_code_list": [
    "<string>"
  ],
  "page": 1,
  "pagesize": 10
}'
```

## 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| username | string | 是 | 用户名 |
| filter_mode | integer | 否 | 过滤模式，默认为 0 |
| filter_user_code | string | 否 | 过滤用户代码 |
| create_start_time | string | 否 | 创建开始时间，ISO 8601 格式 |
| create_end_time | string | 否 | 创建结束时间，ISO 8601 格式 |
| message_source | string | 否 | 消息来源 |
| segment_code_list | array[string] | 否 | 会话代码列表 |
| page | integer | 否 | 页码，默认为 1 |
| pagesize | integer | 否 | 每页大小，默认为 10 |

### 请求体示例

```json
{
  "username": "<string>",
  "filter_mode": 0,
  "filter_user_code": "<string>",
  "create_start_time": "2023-11-07T05:31:56Z",
  "create_end_time": "2023-11-07T05:31:56Z",
  "message_source": "<string>",
  "segment_code_list": [
    "<string>"
  ],
  "page": 1,
  "pagesize": 10
}
```

## 响应示例

### 成功响应 (200)

```json
{
  "code": "<string>",
  "message": "<string>",
  "data": {
    "total": 123,
    "page": 123,
    "page_size": 123,
    "has_next": true,
    "has_previous": true,
    "max_page_num": 123,
    "list": [
      {
        "chat_count": 123,
        "segment_code": "<string>",
        "segment_name": "<string>",
        "user_code": "<string>",
        "create_time": "<string>",
        "message_source": "<string>"
      }
    ]
  }
}
```

## 响应参数说明

### data 对象

| 参数名 | 类型 | 说明 |
|--------|------|------|
| total | integer | 总记录数 |
| page | integer | 当前页码 |
| page_size | integer | 每页大小 |
| has_next | boolean | 是否有下一页 |
| has_previous | boolean | 是否有上一页 |
| max_page_num | integer | 最大页码 |
| list | array | 会话列表 |

### list 数组中的对象

| 参数名 | 类型 | 说明 |
|--------|------|------|
| chat_count | integer | 聊天数量 |
| segment_code | string | 会话代码 |
| segment_name | string | 会话名称 |
| user_code | string | 用户代码 |
| create_time | string | 创建时间 |
| message_source | string | 消息来源 |

## 请求头说明

| 请求头 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| Content-Type | string | 是 | 固定值：`application/json` |
| cybertron-robot-key | string | 是 | API Key |
| cybertron-robot-token | string | 是 | API Token |
| username | string | 是 | 用户名 |
