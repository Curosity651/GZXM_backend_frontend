package com.gzxm.server.modules.achievement;

import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.modules.achievement.domain.AchievementWorkflow;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AchievementWorkflowTest {
    @ParameterizedTest @ValueSource(strings={"PAPER","PATENT","COPYRIGHT","STANDARD","TALENT"})
    void everyUnitActionHasAnExplicitAllowedOrRejectedTransition(String type) {
        var allowed=new HashMap<String,String>();
        allowed.put("DRAFT/SUBMIT_PRE_REVIEW","PRE_INITIAL");allowed.put("PRE_RETURNED/SUBMIT_PRE_REVIEW","PRE_INITIAL");
        allowed.put("FORMAL_DRAFT/SUBMIT_FORMAL","FORMAL_INITIAL");allowed.put("FORMAL_RETURNED/SUBMIT_FORMAL","FORMAL_INITIAL");
        if(Set.of("PAPER","PATENT","COPYRIGHT").contains(type)) {
            String wait="PAPER".equals(type)?"WAIT_PUBLICATION":"PATENT".equals(type)?"WAIT_GRANT":"WAIT_CERTIFICATE";
            allowed.put(wait+"/SUBMIT_SUPPLEMENT","SUPPLEMENT_INITIAL");
            allowed.put("SUPPLEMENT_RETURNED/SUBMIT_SUPPLEMENT","SUPPLEMENT_INITIAL");
        }
        for(String state:List.of("DRAFT","PRE_INITIAL","PRE_FINAL","PRE_RETURNED","FORMAL_DRAFT","FORMAL_INITIAL",
                "FORMAL_FINAL","FORMAL_RETURNED","WAIT_PUBLICATION","WAIT_GRANT","WAIT_CERTIFICATE","SUPPLEMENT_INITIAL","SUPPLEMENT_FINAL","SUPPLEMENT_RETURNED","EFFECTIVE","UNKNOWN"))
            for(String action:List.of("SUBMIT_PRE_REVIEW","SUBMIT_FORMAL","SUBMIT_SUPPLEMENT","UNKNOWN")) {
                String next=allowed.get(state+"/"+action);
                if(next==null) assertThatThrownBy(()->AchievementWorkflow.action(state,action,type)).isInstanceOf(BusinessException.class);
                else assertThat(AchievementWorkflow.action(state,action,type)).isEqualTo(next);
            }
    }
    @ParameterizedTest @ValueSource(strings={"PAPER","PATENT","COPYRIGHT","STANDARD","TALENT"})
    void approvalsReturnsAndFinalRecognitionFollowEachStage(String type) {
        for(String stage:List.of("PRE","FORMAL","SUPPLEMENT")) {
            assertThat(AchievementWorkflow.review(stage+"_INITIAL","APPROVE",type)).isEqualTo(stage+"_FINAL");
            assertThat(AchievementWorkflow.review(stage+"_INITIAL","RETURN",type)).isEqualTo(stage+"_RETURNED");
            assertThat(AchievementWorkflow.review(stage+"_FINAL","RETURN",type)).isEqualTo(stage+"_RETURNED");
        }
        assertThat(AchievementWorkflow.review("PRE_FINAL","APPROVE",type)).isEqualTo("FORMAL_DRAFT");
        assertThat(AchievementWorkflow.review("FORMAL_FINAL","APPROVE",type)).isEqualTo("PAPER".equals(type)?"WAIT_PUBLICATION":"PATENT".equals(type)?"WAIT_GRANT":"COPYRIGHT".equals(type)?"WAIT_CERTIFICATE":"EFFECTIVE");
        assertThat(AchievementWorkflow.review("SUPPLEMENT_FINAL","APPROVE",type)).isEqualTo("EFFECTIVE");
        assertThatThrownBy(()->AchievementWorkflow.review("EFFECTIVE","APPROVE",type)).isInstanceOf(BusinessException.class);
    }
}
