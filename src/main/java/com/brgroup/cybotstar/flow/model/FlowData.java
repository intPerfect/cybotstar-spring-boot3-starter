package com.brgroup.cybotstar.flow.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Flow 响应数据
 * 
 * 包含 Flow 响应的所有顶层字段和 data 字段的结构化定义
 *
 * @author zhiyuan.xi
 */
@Data
public class FlowData {
    /**
     * ⭐状态码；000000 为正常；400 开头为出现错误
     */
    @Nullable
    private String code;

    /**
     * 状态说明
     */
    @Nullable
    private String message;

    /**
     * 报文类型；string 时 data 为字符串；json 时 data 为 json 对象；flow 时 data 为 flow 输出 json 对象
     */
    @Nullable
    private String type;

    /**
     * 参考 v1 接口
     */
    @Nullable
    private Integer index;

    /**
     * 当前运行对话流名称
     */
    @JSONField(name = "flow_name")
    @Nullable
    private String flowName;

    /**
     * 当前运行结点类型
     */
    @JSONField(name = "node_type")
    @Nullable
    private String nodeType;

    /**
     * 当前运行结点名称
     */
    @JSONField(name = "node_title")
    @Nullable
    private String nodeTitle;

    /**
     * 1 为等待用户输出；0 为非等待
     */
    @JSONField(name = "node_waiting_input")
    @Nullable
    private Integer nodeWaitingInput;

    /**
     * 消息体
     */
    @Nullable
    private MessageData data;

    /**
     * Flow 消息数据
     * 
     * 对应响应中的 data 字段，包含所有 Flow 相关的数据信息
     */
    @Data
    public static class MessageData {
        /**
         * 对话历史，数组形式，数组中每个元素为一个节点的人机交互
         */
        @Nullable
        private List<Map<String, Object>> history;

        /**
         * ⭐当前结点的 flow 输出
         */
        @Nullable
        private FlowOutput output;

        /**
         * 当前结点输出（当前仅提供字符串输出）
         */
        @Nullable
        private String answer;

        /**
         * 为 text 时表示 answer 为字符串输出（当前仅提供 text 类型）
         */
        @JSONField(name = "content_type")
        @Nullable
        private String contentType;

        /**
         * 当前 flow 的状态（与 code 对应）
         */
        @JSONField(name = "flow_stage")
        @Nullable
        private String flowStage;

        /**
         * flow 输出的数据信息（debug 模式或正常输出）
         */
        @Nullable
        private String code;

        /**
         * 1 或 0；1 为流式，需要 answer 拼接成最终的
         */
        @JSONField(name = "node_stream")
        @Nullable
        private Integer nodeStream;

        /**
         * 为 stream 时表示 answer 当前拼接序号
         */
        @JSONField(name = "node_answer_index")
        @Nullable
        private Integer nodeAnswerIndex;

        /**
         * y 或 n；为 y 时表示当前结点输出结束
         */
        @JSONField(name = "node_answer_finish")
        @Nullable
        private String nodeAnswerFinish;

        /**
         * 当前结点的 id
         */
        @JSONField(name = "cur_node_id")
        @Nullable
        private String curNodeId;

        /**
         * 当前结点的父结点 id
         */
        @JSONField(name = "parent_node_id")
        @Nullable
        private String parentNodeId;

        /**
         * 转人工方式，仅当 data.code 为 000301 时有值
         * 枚举：2=人工回复（不通知），3=人工回复（邮箱、短信通知），4=仅回复引导话术/API调用，6=SIP协议（webcall）
         */
        @JSONField(name = "manual_reply")
        @Nullable
        private Integer manualReply;

        /**
         * 触发条件，仅当 data.code 为 000301 时有值
         * 枚举：intent=意图，emotion=情绪，unresolve_issue=问题未解决，sensitive_words=敏感词，rag_empty=知识库未召回，flow=对话流转人工节点
         */
        @JSONField(name = "trigger_condition")
        @Nullable
        private String triggerCondition;

        /**
         * manual_reply 对应的描述，仅当 data.code 为 000301 时有值
         * 注意：文档中字段名为 manua_reply_name（可能是拼写错误），但按文档实际字段名实现
         */
        @JSONField(name = "manua_reply_name")
        @Nullable
        private String manualReplyName;

        /**
         * Flow 输出信息
         */
        @Data
        public static class FlowOutput {
            @Nullable
            private Map<String, Object> inputs;

            @Nullable
            private Map<String, Object> variables;

            @Nullable
            private Map<String, Object> entities;

            @JSONField(name = "robot_user_asking")
            @Nullable
            private String robotUserAsking;

            @JSONField(name = "user_robot_replying")
            @Nullable
            private String userRobotReplying;

            @JSONField(name = "robot_user_replying")
            @Nullable
            private String robotUserReplying;
        }
    }
}
