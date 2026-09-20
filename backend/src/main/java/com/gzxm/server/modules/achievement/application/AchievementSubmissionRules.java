package com.gzxm.server.modules.achievement.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.modules.achievement.domain.AchievementEntity;
import com.gzxm.server.modules.achievement.repository.AchievementMaterialMapper;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class AchievementSubmissionRules {
    private static final Map<String,String> FIELD_LABELS=Map.ofEntries(
        Map.entry("acceptanceDate","论文录用时间"),
        Map.entry("publicationDate","论文正式刊出时间"),
        Map.entry("grantDate","专利授权时间"),
        Map.entry("copyrightPublicationDate","软件著作权予以发布日期"),
        Map.entry("draftCommitDate","送审稿提交时间"),
        Map.entry("actualGraduationDate","实际毕业时间"),
        Map.entry("paperStatus","论文状态"),
        Map.entry("patentStatus","专利状态"),
        Map.entry("copyrightStatus","软件著作权状态"),
        Map.entry("receiptDate","专利受理时间"),
        Map.entry("registrationApplicationDate","软件著作权登记申请日期"));
    private static final Map<String,List<String>> FORMAL=Map.of(
        "PAPER",List.of("论文定稿","录用通知或接收函","项目标注页"),
        "PATENT",List.of("专利受理通知书","专利申请文件","项目关联说明"),
        "COPYRIGHT",List.of("软件著作权登记受理通知书","软件鉴别材料","著作权人证明"),
        "STANDARD",List.of("标准送审稿","送审或立项证明"),
        "TALENT",List.of("研究生学位论文证明材料"));
    private final AchievementService service;
    private final AchievementMaterialMapper materials;
    private final AchievementDetailValidator details;
    private final ObjectMapper json;
    public AchievementSubmissionRules(AchievementService service,AchievementMaterialMapper materials,AchievementDetailValidator details,ObjectMapper json) {
        this.service=service;this.materials=materials;this.details=details;this.json=json;
    }
    public void validate(AchievementEntity row,String stage) {
        JsonNode detail;
        try {detail=details.validate(row.getAchievementType(),json.readTree(row.getDetailJson()));}
        catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("Invalid achievement detail",ex);}
        if(row.getTitle()==null || row.getTitle().isBlank() || row.getResponsiblePerson()==null || row.getResponsiblePerson().isBlank())
            throw BusinessException.validation("ACHIEVEMENT_REQUIRED_FIELDS","成果标题和负责人必填");
        String type=row.getAchievementType();
        if("PRE_REVIEW".equals(stage)) {
            requireStageStatus(detail,type,stage);
            return;
        }
        var gateway=service.gateway(); // Missing A capability must fail even when no attachments exist.
        List<String> required=new ArrayList<>();
        if("FORMAL".equals(stage)) {
            required.addAll(FORMAL.get(type));
            String date=switch(type) {case "PAPER"->"acceptanceDate";case "PATENT"->"receiptDate";case "COPYRIGHT"->"registrationApplicationDate";case "STANDARD"->"draftCommitDate";case "TALENT"->"actualGraduationDate";default->throw new IllegalStateException("Unknown type");};
            requireText(detail,date);
            requireStageStatus(detail,type,stage);
        } else if("SUPPLEMENT".equals(stage) && "PAPER".equals(type)) {
            requireText(detail,"publicationDate");requireValue(detail,"paperStatus",Set.of("已正式刊出"));
            required.addAll(List.of("正式刊出论文全文","期刊封面、目录及见刊页","项目标注页"));
            if(Set.of("SCI","EI","CSCD").contains(detail.path("paperType").asText())) required.add("检索证明");
            if(detail.path("isChineseCoreJournal").asBoolean()) required.add("中文核心期刊认定证明");
        } else if("SUPPLEMENT".equals(stage) && "PATENT".equals(type)) {
            requireText(detail,"grantDate");requireValue(detail,"patentStatus",Set.of("已授权"));
            required.addAll(List.of("专利授权证书","授权公告文本","法律状态证明","专利权属证明"));
        } else if("SUPPLEMENT".equals(stage) && "COPYRIGHT".equals(type)) {
            requireText(detail,"copyrightPublicationDate");requireValue(detail,"copyrightStatus",Set.of("已予以发布"));
            required.addAll(List.of("软件著作权予以发布证明","登记信息证明","著作权人证明"));
        } else throw BusinessException.conflict("INVALID_ACHIEVEMENT_STAGE","成果不支持此提交阶段");
        var current=materials.list(row.getId()).stream().filter(AchievementMaterialMapper.Material::active).toList();
        var present=new HashSet<String>();
        for(var material:current) {
            var file=gateway.readMetadata(material.fileId());
            if(!"READY".equals(file.status())) throw BusinessException.validation("MATERIAL_NOT_READY","提交材料尚未完成上传");
            present.add(material.materialType());
        }
        if(!present.containsAll(required)) {
            required.removeAll(present);
            throw BusinessException.validation("ACHIEVEMENT_MATERIALS_REQUIRED","缺少材料："+String.join("、",required));
        }
    }
    private void requireText(JsonNode detail,String key) {
        if(detail.path(key).asText().isBlank()) throw BusinessException.validation("ACHIEVEMENT_REQUIRED_FIELDS",label(key)+"为本阶段必填项");
    }
    private void requireValue(JsonNode detail,String key,Set<String> values) {
        if(!values.contains(detail.path(key).asText())) throw BusinessException.validation("ACHIEVEMENT_RECOGNITION_REQUIRED",label(key)+"未满足本阶段认定条件，可选值："+String.join("、",values));
    }
    private void requireStageStatus(JsonNode detail,String type,String stage) {
        switch(type) {
            case "PAPER" -> requireValue(detail,"paperStatus",Set.of(switch(stage) {case "PRE_REVIEW"->"撰写/投稿准备";case "FORMAL"->"已录用";default->"已正式刊出";}));
            case "PATENT" -> requireValue(detail,"patentStatus",Set.of(switch(stage) {case "PRE_REVIEW"->"申请材料准备/已申请";case "FORMAL"->"已受理";default->"已授权";}));
            case "COPYRIGHT" -> requireValue(detail,"copyrightStatus",Set.of(switch(stage) {case "PRE_REVIEW"->"申请材料准备/已申请";case "FORMAL"->"已受理";default->"已予以发布";}));
            default -> { }
        }
    }
    private String label(String key) {return FIELD_LABELS.getOrDefault(key,key);}
}
