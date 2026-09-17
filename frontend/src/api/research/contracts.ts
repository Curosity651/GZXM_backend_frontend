// Generated from docs/api/openapi.yaml. Run generate-research-types.py; do not edit.

export type Achievement = { "id": string; "topicId": string; "unitId": string; "nodeId": string; "indicatorDefinitionId": string; "achievementType": string; "title": string; "responsiblePerson"?: string; "status": AchievementStatus; "countsToIndicator"?: boolean; "recordVersion": number; "submittedVersion": number; "detail"?: AchievementDetail; "materialLinks"?: Array<AchievementMaterialLink>; "materials": Array<FileObject>; "approvals"?: Array<ApprovalRecord>; "createdAt"?: string; "updatedAt"?: string; };

export type AchievementActionRequest = { "action": "SUBMIT_PRE_REVIEW" | "REGISTER_EXTERNAL_SUBMISSION" | "START_FORMAL" | "SUBMIT_FORMAL" | "SUBMIT_SUPPLEMENT"; "recordVersion": number; "externalSubmissionDate"?: string; "externalSubmissionNumber"?: string; };

export type AchievementDetail = (PaperAchievementDetail | PatentAchievementDetail | CopyrightAchievementDetail | StandardAchievementDetail | TalentAchievementDetail);

export type AchievementMaterialInput = { "fileId": string; "materialType": string; };

export type AchievementMaterialLink = { "id": string; "fileId": string; "materialType": string; "version": number; "active": boolean; "status": string; };

export type AchievementPage = { "items": Array<Achievement>; "page": number; "size": number; "total": number; };

export type AchievementProgress = { "nodeId": string; "countingBasis": "CUMULATIVE_NODE_CURRENT_FACTS"; "baseTotals": { "PAPER": number; "PATENT": number; "COPYRIGHT": number; "STANDARD": number; "TALENT": number; }; "baseStages": AchievementStageCounts; "specialIndicators": Array<AchievementProgressRow>; "rows": Array<AchievementProgressRow>; };

export type AchievementProgressRow = { "scope": "TOPIC" | "UNIT"; "topicId": string; "unitId": string | null; "nodeId": string; "indicatorDefinitionId": string; "achievementType": "PAPER" | "PATENT" | "COPYRIGHT" | "STANDARD" | "TALENT"; "targetQuantity": number | null; "targetVersion": number | null; "targetPublished": boolean; "hasTarget": boolean; "completionRate": number | null; "historical": boolean; "stages": AchievementStageCounts; };

export type AchievementReviewRequest = { "decision": "APPROVE" | "RETURN"; "opinion"?: string; "recordVersion": number; "submittedVersion": number; };

export type AchievementStageCounts = { "initiated": number; "preApproved": number; "external": number; "formal": number; "supplement": number; "effective": number; };

export type AchievementStatus = "DRAFT" | "PRE_INITIAL" | "PRE_FINAL" | "PRE_RETURNED" | "PRE_APPROVED" | "EXTERNAL_SUBMITTED" | "FORMAL_DRAFT" | "FORMAL_INITIAL" | "FORMAL_FINAL" | "FORMAL_RETURNED" | "WAIT_PUBLICATION" | "WAIT_GRANT" | "SUPPLEMENT_INITIAL" | "SUPPLEMENT_FINAL" | "SUPPLEMENT_RETURNED" | "EFFECTIVE";

export type AchievementWriteRequest = { "topicId": string; "nodeId": string; "indicatorDefinitionId": string; "title": string; "responsiblePerson": string; "detail"?: AchievementDetail; "materialAttachments"?: Array<AchievementMaterialInput>; "materialFileIds"?: Array<string>; "recordVersion"?: number; };

export type ApprovalRecord = { "id": string; "businessType": "ACHIEVEMENT" | "REPORT"; "businessId": string; "stage": string; "level": "INITIAL" | "FINAL"; "decision": "APPROVED" | "RETURNED"; "opinion"?: string; "operatorId": string; "operatedAt": string; "submittedVersion": number; };

export type CopyrightAchievementDetail = { "certificateDate"?: (string | "" | null); "completionDate"?: (string | "" | null); "copyrightOwnerList"?: string | null; "developers"?: string | null; "developmentLanguage"?: string | null; "developmentMode"?: string | null; "firstCompleter"?: string | null; "firstCopyrightOwner"?: string | null; "firstPublicationDate"?: (string | "" | null); "isPowerGridFirstCompleter"?: boolean | null; "operatingPlatform"?: string | null; "registrationApplicationDate"?: (string | "" | null); "registrationNumber"?: string | null; "remarks"?: string | null; "rightsScope"?: string | null; "shortName"?: string | null; "softwareCategory"?: string | null; "softwareMainFunctions"?: string | null; "technicalFeatures"?: string | null; "version"?: string | null; };

export type FileObject = { "id": string; "originalName": string; "size": number; "contentType": string; "sha256"?: string; "status": "PENDING" | "READY" | "DELETED"; "uploaderId": string; "createdAt": string; };

export type IndicatorDefinition = { "id": string; "code": string; "name": string; "achievementType": "PAPER" | "PATENT" | "COPYRIGHT" | "STANDARD" | "TALENT"; "unit": string; "category": "BASE" | "SPECIAL"; "enabled"?: boolean; };

export type IndicatorTargetBatch = { "nodeId": string; "draftVersion"?: number; "targets": Array<{ "indicatorDefinitionId": string; "targetQuantity": number; }>; };

export type PaperAchievementDetail = { "abstract"?: string | null; "acceptanceDate"?: (string | "" | null); "allAuthors"?: string | null; "cnNumber"?: string | null; "correspondingAuthor"?: string | null; "doi"?: string | null; "englishTitle"?: string | null; "externalSubmissionNumber"?: string | null; "firstAuthor"?: string | null; "firstSigningUnit"?: string | null; "intendedJournal"?: string | null; "isChineseCoreJournal"?: boolean | null; "isPowerGridFirstAuthor"?: boolean | null; "issn"?: string | null; "journalLevel"?: string | null; "journalName"?: string | null; "keywords"?: string | null; "paperFormType"?: "期刊论文" | "会议论文" | "" | null; "paperStatus"?: "撰写中" | "已投稿" | "已录用" | "已正式刊出" | "" | null; "paperType"?: "SCI" | "EI" | "CSCD" | "其他" | "无" | "" | null; "projectLabeling"?: string | null; "publicationDate"?: (string | "" | null); "remarks"?: string | null; "researchDirection"?: string | null; "signingUnitList"?: string | null; "submissionDate"?: (string | "" | null); };

export type PatentAchievementDetail = { "abstract"?: string | null; "applicantList"?: string | null; "applicationCountry"?: string | null; "applicationDate"?: (string | "" | null); "applicationNumber"?: string | null; "firstApplicant"?: string | null; "grantDate"?: (string | "" | null); "grantPublicationNumber"?: string | null; "inventorList"?: string | null; "isPowerGridFirstApplicant"?: boolean | null; "legalStatus"?: string | null; "ownershipDescription"?: string | null; "patentScope"?: "国内" | "国际" | "" | null; "patentStatus"?: "申请材料准备中" | "已申请" | "已受理" | "已授权" | "" | null; "publicationNumber"?: string | null; "receiptDate"?: (string | "" | null); "receiptNumber"?: string | null; "remarks"?: string | null; "technicalField"?: string | null; };

export type StandardAchievementDetail = { "currentStage"?: string | null; "draftCommitDate"?: (string | "" | null); "draftSubmissionDate"?: (string | "" | null); "drafters"?: string | null; "leadingUnit"?: string | null; "participatingUnits"?: string | null; "remarks"?: string | null; "responsibleOrganization"?: string | null; "standardLevel"?: string | null; };

export type SubmissionSnapshot = { "id": string; "businessType": "ACHIEVEMENT" | "REPORT"; "businessId": string; "stage": string; "submittedVersion": number; "submittedAt": string; "submitterId": string; "payload": Record<string, unknown>; };

export type TalentAchievementDetail = { "actualGraduationDate"?: (string | "" | null); "educationLevel"?: "博士" | "硕士" | "" | null; "enrollmentDate"?: (string | "" | null); "expectedGraduationDate"?: (string | "" | null); "remarks"?: string | null; "studentName"?: string | null; "supervisorName"?: string | null; "thesisTitle"?: string | null; "trainingStatus"?: string | null; "trainingUnit"?: string | null; };

export type TimeNode = { "id": string; "name": string; "deadline": string; "sortOrder": number; "enabled": boolean; };

export type Topic = { "id": string; "code": string; "name": string; "summary"?: string; "leadUnitId": string; "status": TopicStatus; "enabled": boolean; "startDate"?: string; "endDate"?: string; "recordVersion": number; "members"?: Array<TopicMembership>; };

export type TopicIndicator = { "id": string; "topicId": string; "nodeId": string; "indicatorDefinitionId": string; "targetQuantity": number; "status": "DRAFT" | "PUBLISHED"; "version": number; };

export type TopicMembership = { "id": string; "topicId": string; "unitId": string; "unitName"?: string; "membershipType": "LEAD" | "PARTICIPANT"; "enabled": boolean; };

export type TopicPage = { "items": Array<Topic>; "page": number; "size": number; "total": number; };

export type TopicStatus = "DRAFT" | "ACTIVE" | "PAUSED" | "CLOSED";

export type TopicWriteRequest = { "code": string; "name": string; "summary"?: string; "leadUnitId": string; "participantUnitIds"?: Array<string>; "startDate"?: string; "endDate"?: string; "recordVersion"?: number; };

export type Unit = { "id": string; "code": string; "name": string; "internal": boolean; "enabled": boolean; };

export type UnitAllocationBatch = { "nodeId": string; "draftVersion"?: number; "allocations": Array<{ "unitId": string; "indicatorDefinitionId": string; "targetQuantity": number; }>; };

export type UnitIndicatorAllocation = { "id": string; "topicId": string; "unitId": string; "nodeId": string; "indicatorDefinitionId": string; "targetQuantity": number; "status": "DRAFT" | "PUBLISHED"; "version": number; };
