package org.dreamwork.dsi.embedded.httpd.support.sse;

/**
 * 「没有可用通道」异常。
 *
 * <p>当 SSE 会话是<b>订阅者</b>且不允许动态附着时，它只能加入那些<b>已存在生产者</b>的通道；
 * 若目标通道一个都不满足该条件，就会抛出本异常（见 {@code SseHub#attach} /
 * {@link IServerSideEvent#attach}）。</p>
 *
 * <p>业务侧一般不直接处理它：注册阶段的失败会被框架转换成一条 {@code NO_AVAILABLE_CHANNEL}
 * 的 error 事件推送给客户端；动态附着失败则可由调用方自行决定是否重试或忽略。</p>
 */
public class NoAvailableException extends Exception {
    /** 创建一个不带任何信息的异常。 */
    public NoAvailableException () {
    }

    /**
     * @param message 异常描述
     */
    public NoAvailableException (String message) {
        super (message);
    }

    /**
     * @param message 异常描述
     * @param cause   原始异常
     */
    public NoAvailableException (String message, Throwable cause) {
        super (message, cause);
    }

    /**
     * @param cause 原始异常
     */
    public NoAvailableException (Throwable cause) {
        super (cause);
    }

    /**
     * @param message            异常描述
     * @param cause              原始异常
     * @param enableSuppression  是否启用抑制
     * @param writableStackTrace 是否可写堆栈
     */
    public NoAvailableException (String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super (message, cause, enableSuppression, writableStackTrace);
    }
}
