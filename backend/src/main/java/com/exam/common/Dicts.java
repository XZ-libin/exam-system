package com.exam.common;

/**
 * 状态与类型常量。数据库里存的都是这些数字，改值要同步改 db/02_seed.sql。
 */
public final class Dicts {

    private Dicts() {
    }

    /** 角色编码，对应 sys_role.code */
    public static final class Role {
        public static final String ADMIN = "ADMIN";
        public static final String TEACHER = "TEACHER";
        public static final String STUDENT = "STUDENT";

        private Role() {
        }
    }

    /** 题型，对应 qz_question.q_type / ex_paper_question.q_type */
    public static final class QType {
        public static final int SINGLE = 1;
        public static final int MULTIPLE = 2;
        public static final int JUDGE = 3;
        public static final int BLANK = 4;
        public static final int ESSAY = 5;

        private QType() {
        }

        public static String name(Integer type) {
            if (type == null) {
                return "未知";
            }
            return switch (type) {
                case SINGLE -> "单选题";
                case MULTIPLE -> "多选题";
                case JUDGE -> "判断题";
                case BLANK -> "填空题";
                case ESSAY -> "简答题";
                default -> "未知题型";
            };
        }

        /** 需要人工阅卷的题型 */
        public static boolean needsReview(Integer type) {
            return type != null && type == ESSAY;
        }
    }

    /** 通用启用状态 */
    public static final class Status {
        public static final int DISABLED = 0;
        public static final int ENABLED = 1;

        private Status() {
        }
    }

    /** 试卷状态 ex_paper.status */
    public static final class PaperStatus {
        public static final int DRAFT = 0;
        public static final int PUBLISHED = 1;
        public static final int ARCHIVED = 2;

        private PaperStatus() {
        }
    }

    /** 组卷方式 ex_paper.build_type */
    public static final class BuildType {
        public static final int MANUAL = 1;
        public static final int AUTO = 2;

        private BuildType() {
        }
    }

    /** 考试场次状态 ex_exam.status，进行中/结束由时间推导，这里只存人工状态 */
    public static final class ExamStatus {
        public static final int DRAFT = 0;
        public static final int PUBLISHED = 1;
        public static final int CLOSED = 3;

        private ExamStatus() {
        }
    }

    /** 考试可见范围 ex_exam.audience_type */
    public static final class Audience {
        public static final int ALL = 1;
        public static final int CLASS = 2;
        public static final int NAMED = 3;

        private Audience() {
        }
    }

    /** 答卷状态 an_exam_record.status */
    public static final class RecordStatus {
        public static final int DOING = 0;
        public static final int WAIT_REVIEW = 1;
        public static final int FINISHED = 2;
        public static final int TIMEOUT_SUBMIT = 3;

        private RecordStatus() {
        }

        /** 已交卷（含超时强制交卷） */
        public static boolean submitted(Integer status) {
            return status != null && (status == WAIT_REVIEW || status == FINISHED || status == TIMEOUT_SUBMIT);
        }
    }

    /** 单题判分状态 an_answer_item.review_status */
    public static final class ReviewStatus {
        public static final int AUTO_JUDGED = 0;
        public static final int WAIT_REVIEW = 1;
        public static final int REVIEWED = 2;

        private ReviewStatus() {
        }
    }

    /** 单题对错 an_answer_item.correct_flag */
    public static final class CorrectFlag {
        public static final int WRONG = 0;
        public static final int RIGHT = 1;
        public static final int HALF = 2;

        private CorrectFlag() {
        }
    }

    /** 成绩是否发布 ex_exam.score_published */
    public static final class ScorePublish {
        public static final int UNPUBLISHED = 0;
        public static final int PUBLISHED = 1;

        private ScorePublish() {
        }
    }
}
