package cn.lwccxy.agentcraft.entity.record.pptx;

/**
 * 意图识别结果
 */
public class PptIntentResult {
    private PptIntent intent;
    private String reason;

    public PptIntentResult(PptIntent intent, String reason) {
        this.intent = intent;
        this.reason = reason;
    }

    public PptIntent getIntent() {
        return intent;
    }

    public String getReason() {
        return reason;
    }
}
