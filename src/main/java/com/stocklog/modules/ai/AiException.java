package com.stocklog.modules.ai;

/** AI 호출 실패를 종류별로 나눈다 */
public class AiException extends RuntimeException {

    public enum Kind {
        /** 하루(또는 결제 한도) 사용량 소진 */
        QUOTA_DAILY,
        /** 분당 한도 */
        RATE_LIMIT,
        /** 안전 필터 등으로 본문이 비었다 */
        BLOCKED,
        /** 네트워크 · 응답 형식 · 서버 오류 */
        ERROR
    }

    private final Kind kind;

    public AiException(Kind kind, String detail) {
        super(detail);
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    public boolean isQuota() { return kind == Kind.QUOTA_DAILY || kind == Kind.RATE_LIMIT; }

    /** 화면에 그대로 보여 줄 문장 */
    public String userMessage() {
        return switch (kind) {
            case QUOTA_DAILY -> "오늘 쓸 수 있는 AI 사용량을 다 썼어요. 내일 다시 받을 수 있어요.";
            case RATE_LIMIT -> "AI 요청이 잠깐 몰렸어요. 1분쯤 뒤에 다시 눌러 주세요.";
            case BLOCKED -> "AI가 이번 내용에는 답을 주지 않았어요. 잠시 뒤 다시 눌러 주세요.";
            case ERROR -> "AI 응답을 받지 못했어요. 잠시 뒤 다시 눌러 주세요.";
        };
    }

    /** API 응답 코드 문자열: QUOTA(소진) / ERROR(그 밖) */
    public String code() { return isQuota() ? "QUOTA" : "ERROR"; }
}
