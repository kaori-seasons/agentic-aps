package cn.bctools.aps.solve.enums;

import lombok.Getter;

/**
 * @author jvs
 * 排程进度状态
 */
@Getter
public enum SolveProgressStatusEnum {

    /**
     * 没有排程进度
     */
    NONE,

    /**
     * 排程中
     */
    SCHEDULING,

    /**
     * 排程成功
     */
    SUCCESS,

    /**
     * 排程失败
     */
    FAILURE,

    /**
     * 不需要排程
     */
    NO_SCHEDULED;

}
