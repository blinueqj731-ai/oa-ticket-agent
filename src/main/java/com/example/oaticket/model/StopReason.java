package com.example.oaticket.model;

/**
 * 循环结束的原因
 *
 * 区分正常收敛和被迫中止，统计降级比例时要靠它
 */
public enum StopReason {

    /** 模型不再请求工具，主动给出结论 */
    COMPLETED("正常完成"),

    /** 达到步数上限仍未收敛 */
    MAX_STEPS("达到步数上限"),

    /** 超过墙钟超时 */
    TIMEOUT("处理超时"),

    /** 超过 token 预算 */
    TOKEN_BUDGET("token 预算耗尽");

    private final String displayName;

    StopReason(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 取中文说明
     *
     * @return 用于展示的中文名称
     */
    public String displayName() {
        return displayName;
    }
}
