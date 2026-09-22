package com.exam.common;

/**
 * 业务错误码。1xxx 通用/用户，2xxx 题库，3xxx 试卷，4xxx 考试，5xxx 判分。
 */
public final class ErrorCode {

    public static final int OK = 0;

    public static final int UNAUTHORIZED = 1001;
    public static final int FORBIDDEN = 1002;
    public static final int BAD_PARAM = 1003;
    public static final int NOT_FOUND = 1004;
    public static final int SERVER_ERROR = 1500;

    public static final int LOGIN_FAILED = 1010;
    public static final int ACCOUNT_DISABLED = 1011;
    public static final int USERNAME_EXISTS = 1012;
    public static final int OLD_PASSWORD_WRONG = 1013;

    public static final int CATEGORY_HAS_QUESTION = 2010;
    public static final int QUESTION_NOT_FOUND = 2011;
    public static final int QUESTION_USED_BY_PAPER = 2012;
    public static final int QUESTION_OPTION_INVALID = 2013;

    public static final int PAPER_NOT_FOUND = 3010;
    public static final int PAPER_PUBLISHED_LOCKED = 3011;
    public static final int PAPER_EMPTY = 3012;
    public static final int PAPER_SCORE_NOT_MATCH = 3013;
    public static final int PAPER_QUESTION_NOT_ENOUGH = 3014;
    public static final int PAPER_DUPLICATE_QUESTION = 3015;

    public static final int EXAM_NOT_FOUND = 4010;
    public static final int EXAM_NOT_START = 4011;
    public static final int EXAM_ENDED = 4012;
    public static final int EXAM_TOO_LATE = 4013;
    public static final int EXAM_NO_PERMISSION = 4014;
    public static final int EXAM_ATTEMPT_LIMIT = 4015;
    public static final int EXAM_IN_PROGRESS_CANNOT_EDIT = 4016;
    public static final int EXAM_HAS_RECORD = 4017;
    public static final int EXAM_SCORE_NOT_PUBLISHED = 4018;

    public static final int RECORD_NOT_FOUND = 4050;
    public static final int RECORD_ALREADY_SUBMITTED = 4051;
    public static final int RECORD_TIME_UP = 4052;
    public static final int RECORD_SWITCH_LIMIT = 4053;

    public static final int GRADING_NOT_PENDING = 5010;
    public static final int GRADING_SCORE_INVALID = 5011;

    private ErrorCode() {
    }
}
