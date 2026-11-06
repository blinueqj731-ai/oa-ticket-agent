package com.example.oaticket.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间格式化
 *
 * 三处都在用同一个格式：健康检查的时间戳、提案创建时间、确认与审计日志时间。
 * 抽出来是为了保证格式绝对一致——提案表和日志表的时间如果格式不同，跨表核对会很痛苦
 */
public final class DateTimeUtils {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DateTimeUtils() {
    }

    /**
     * 当前时间的标准字符串表示
     *
     * @return 形如 2026-11-05 16:58:00 的时间字符串
     */
    public static String now() {
        return LocalDateTime.now().format(DATE_TIME);
    }
}
