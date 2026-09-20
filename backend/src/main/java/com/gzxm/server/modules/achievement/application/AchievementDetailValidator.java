package com.gzxm.server.modules.achievement.application;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.gzxm.server.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.*;

/** Field names taken from the five existing frontend forms; submit-stage requiredness is separate. */
@Component
public class AchievementDetailValidator {
    public static final Map<String,Set<String>> FIELDS=Map.of(
            "PAPER", Set.of("abstract","acceptanceDate","allAuthors","cnNumber","correspondingAuthor","doi","englishTitle","externalSubmissionNumber","firstAuthor","firstSigningUnit","intendedJournal","isChineseCoreJournal","isPowerGridFirstAuthor","issn","journalLevel","journalName","keywords","paperFormType","paperStatus","paperType","projectLabeling","publicationDate","remarks","researchDirection","signingUnitList","submissionDate"),
            "PATENT", Set.of("abstract","applicantList","applicationCountry","applicationDate","applicationNumber","firstApplicant","grantDate","grantPublicationNumber","inventorList","isPowerGridFirstApplicant","legalStatus","ownershipDescription","patentScope","patentStatus","publicationNumber","receiptDate","receiptNumber","remarks","technicalField"),
            "COPYRIGHT", Set.of("copyrightStatus","certificateDate","completionDate","copyrightOwnerList","developers","developmentLanguage","developmentMode","firstCompleter","firstCopyrightOwner","firstPublicationDate","isPowerGridFirstCompleter","operatingPlatform","registrationApplicationDate","registrationNumber","remarks","rightsScope","shortName","softwareCategory","softwareMainFunctions","technicalFeatures","version"),
            "STANDARD", Set.of("currentStage","draftCommitDate","draftSubmissionDate","drafters","leadingUnit","participatingUnits","remarks","responsibleOrganization","standardLevel"),
            "TALENT", Set.of("actualGraduationDate","educationLevel","enrollmentDate","expectedGraduationDate","remarks","studentName","supervisorName","thesisTitle","trainingStatus","trainingUnit"));
    private static final Map<String,Set<String>> ENUMS=Map.of(
        "paperStatus",Set.of("撰写/投稿准备","已录用","已正式刊出"),
        "paperFormType",Set.of("期刊论文","会议论文"),
        "paperType",Set.of("SCI","EI","CSCD","其他","无"),
        "patentStatus",Set.of("申请材料准备/已申请","已受理","已授权"),
        "patentScope",Set.of("国内","国际"),"educationLevel",Set.of("博士","硕士"),
        "copyrightStatus",Set.of("申请材料准备/已申请","已受理","已取得登记证书"));
    private static final Set<String> LONG_TEXT=Set.of("remarks","abstract","signingUnitList","softwareMainFunctions","technicalFeatures","ownershipDescription");
    public JsonNode validate(String type,JsonNode input) {
        var allowed=FIELDS.get(type);
        if(allowed==null) throw invalid("不支持的成果类型");
        if(input==null || input.isNull()) return JsonNodeFactory.instance.objectNode();
        if(!input.isObject()) throw invalid("detail必须为对象");
        var result=JsonNodeFactory.instance.objectNode();
        input.fields().forEachRemaining(entry->{
            String key=entry.getKey(); var value=entry.getValue();
            if(!allowed.contains(key)) throw invalid("该成果类型不支持字段："+key);
            if(value.isNull() || (value.isTextual() && value.asText().isBlank())) return;
            if(key.matches("is[A-Z].*")) {
                if(!value.isBoolean()) throw invalid(key+"必须是布尔值");
            } else {
                if(!value.isTextual()) throw invalid(key+"必须是字符串");
                String text=value.asText();
                if(text.length()>(LONG_TEXT.contains(key)?10000:500)) throw invalid(key+"超出长度限制");
                if(ENUMS.containsKey(key) && !ENUMS.get(key).contains(text)) throw invalid(key+"枚举值不正确");
                if(key.endsWith("Date")) {
                    try { if(!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException(); LocalDate.parse(text); }
                    catch(RuntimeException ex) { throw invalid(key+"必须是有效YYYY-MM-DD日期"); }
                }
            }
            result.set(key,value.deepCopy());
        });
        for(String[] pair:List.of(new String[]{"submissionDate","acceptanceDate"},new String[]{"acceptanceDate","publicationDate"},
                new String[]{"applicationDate","receiptDate"},new String[]{"receiptDate","grantDate"},
                new String[]{"completionDate","registrationApplicationDate"},new String[]{"registrationApplicationDate","certificateDate"},
                new String[]{"draftSubmissionDate","draftCommitDate"},new String[]{"enrollmentDate","expectedGraduationDate"},
                new String[]{"enrollmentDate","actualGraduationDate"})) {
            if(result.has(pair[0]) && result.has(pair[1]) && LocalDate.parse(result.path(pair[0]).asText()).isAfter(LocalDate.parse(result.path(pair[1]).asText())))
                throw invalid(pair[1]+"不能早于"+pair[0]);
        }
        if(result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65535) throw invalid("detail总长度超过65535字节");
        return result;
    }
    private static BusinessException invalid(String message) { return BusinessException.validation("INVALID_ACHIEVEMENT_DETAIL",message); }
}
