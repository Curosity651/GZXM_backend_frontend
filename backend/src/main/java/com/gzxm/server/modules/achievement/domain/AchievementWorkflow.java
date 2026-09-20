package com.gzxm.server.modules.achievement.domain;

import com.gzxm.server.common.exception.BusinessException;
import java.util.Set;

/** Explicit business transitions. DRAFT remains compatible with step 6 records. */
public final class AchievementWorkflow {
    private AchievementWorkflow() {}
    public static final Set<String> EDITABLE=Set.of("DRAFT","PRE_RETURNED","FORMAL_DRAFT","FORMAL_RETURNED","WAIT_PUBLICATION","WAIT_GRANT","WAIT_CERTIFICATE","SUPPLEMENT_RETURNED");
    public static final Set<String> INITIAL=Set.of("PRE_INITIAL","FORMAL_INITIAL","SUPPLEMENT_INITIAL");
    public static final Set<String> FINAL=Set.of("PRE_FINAL","FORMAL_FINAL","SUPPLEMENT_FINAL");
    public static String action(String status,String action,String type) {
        if("SUBMIT_PRE_REVIEW".equals(action) && Set.of("DRAFT","PRE_RETURNED").contains(status)) return "PRE_INITIAL";
        if("SUBMIT_FORMAL".equals(action) && Set.of("FORMAL_DRAFT","FORMAL_RETURNED").contains(status)) return "FORMAL_INITIAL";
        if("SUBMIT_SUPPLEMENT".equals(action) && (("PAPER".equals(type) && "WAIT_PUBLICATION".equals(status))
                || ("PATENT".equals(type) && "WAIT_GRANT".equals(status))
                || ("COPYRIGHT".equals(type) && "WAIT_CERTIFICATE".equals(status))
                || (supplemented(type) && "SUPPLEMENT_RETURNED".equals(status)))) return "SUPPLEMENT_INITIAL";
        throw BusinessException.conflict("ILLEGAL_ACHIEVEMENT_TRANSITION","当前状态不允许该成果动作");
    }
    public static String review(String status,String decision,String type) {
        if(!INITIAL.contains(status) && !FINAL.contains(status)) throw BusinessException.conflict("ACHIEVEMENT_NOT_REVIEWABLE","当前不在审批阶段");
        if("RETURN".equals(decision)) return status.startsWith("PRE_")?"PRE_RETURNED":status.startsWith("FORMAL_")?"FORMAL_RETURNED":"SUPPLEMENT_RETURNED";
        if(!"APPROVE".equals(decision)) throw BusinessException.validation("INVALID_REVIEW_DECISION","审批结论不正确");
        if(INITIAL.contains(status)) return status.replace("_INITIAL","_FINAL");
        if("PRE_FINAL".equals(status)) return "FORMAL_DRAFT";
        if("FORMAL_FINAL".equals(status)) return "PAPER".equals(type)?"WAIT_PUBLICATION":"PATENT".equals(type)?"WAIT_GRANT":"COPYRIGHT".equals(type)?"WAIT_CERTIFICATE":"EFFECTIVE";
        return "EFFECTIVE";
    }
    public static String stage(String status) {
        if(status.startsWith("PRE_")) return "PRE_REVIEW";
        if(status.startsWith("FORMAL_")) return "FORMAL";
        if(status.startsWith("SUPPLEMENT_")) return "SUPPLEMENT";
        throw BusinessException.conflict("ACHIEVEMENT_NOT_REVIEWABLE","当前不在提交审批阶段");
    }
    private static boolean supplemented(String type) {return Set.of("PAPER","PATENT","COPYRIGHT").contains(type);}
}
