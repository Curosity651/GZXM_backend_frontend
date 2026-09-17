// Generated from OpenAPI and existing B form labels; run generate-research-fields.py.
export interface DetailField { key: string; label: string; kind: string; maxLength: number; options?: string[] }
export const detailFields: Record<string, DetailField[]> = {
  "PAPER": [
    {
      "key": "abstract",
      "label": "摘要",
      "kind": "text",
      "maxLength": 10000
    },
    {
      "key": "acceptanceDate",
      "label": "录用时间",
      "kind": "date",
      "maxLength": 500
    },
    {
      "key": "allAuthors",
      "label": "全部作者",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "cnNumber",
      "label": "CN号",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "correspondingAuthor",
      "label": "通讯作者",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "doi",
      "label": "DOI",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "englishTitle",
      "label": "英文题目",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "externalSubmissionNumber",
      "label": "投稿编号",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "firstAuthor",
      "label": "第一作者",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "firstSigningUnit",
      "label": "第一署名单位",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "intendedJournal",
      "label": "拟投期刊/会议",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "isChineseCoreJournal",
      "label": "是否为中文核心期刊",
      "kind": "boolean",
      "maxLength": 500
    },
    {
      "key": "isPowerGridFirstAuthor",
      "label": "是否为广西电网第一作者",
      "kind": "boolean",
      "maxLength": 500
    },
    {
      "key": "issn",
      "label": "ISSN号",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "journalLevel",
      "label": "期刊级别",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "journalName",
      "label": "期刊/会议名称",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "keywords",
      "label": "关键词",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "paperFormType",
      "label": "论文类型",
      "kind": "text",
      "maxLength": 500,
      "options": [
        "期刊论文",
        "会议论文"
      ]
    },
    {
      "key": "paperStatus",
      "label": "论文状态",
      "kind": "text",
      "maxLength": 500,
      "options": [
        "撰写中",
        "已投稿",
        "已录用",
        "已正式刊出"
      ]
    },
    {
      "key": "paperType",
      "label": "收录类别",
      "kind": "text",
      "maxLength": 500,
      "options": [
        "SCI",
        "EI",
        "CSCD",
        "其他",
        "无"
      ]
    },
    {
      "key": "projectLabeling",
      "label": "项目名称/编号标注情况",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "publicationDate",
      "label": "正式刊出时间",
      "kind": "date",
      "maxLength": 500
    },
    {
      "key": "remarks",
      "label": "备注",
      "kind": "text",
      "maxLength": 10000
    },
    {
      "key": "researchDirection",
      "label": "研究方向",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "signingUnitList",
      "label": "作者及署名单位排序",
      "kind": "text",
      "maxLength": 10000
    },
    {
      "key": "submissionDate",
      "label": "投稿时间",
      "kind": "date",
      "maxLength": 500
    }
  ],
  "PATENT": [
    {
      "key": "abstract",
      "label": "专利摘要",
      "kind": "text",
      "maxLength": 10000
    },
    {
      "key": "applicantList",
      "label": "申请人及排序",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "applicationCountry",
      "label": "申请国家/地区",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "applicationDate",
      "label": "申请时间",
      "kind": "date",
      "maxLength": 500
    },
    {
      "key": "applicationNumber",
      "label": "申请号",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "firstApplicant",
      "label": "第一申请人",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "grantDate",
      "label": "授权时间",
      "kind": "date",
      "maxLength": 500
    },
    {
      "key": "grantPublicationNumber",
      "label": "授权公告号",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "inventorList",
      "label": "发明人及排序",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "isPowerGridFirstApplicant",
      "label": "是否为广西电网第一申请人",
      "kind": "boolean",
      "maxLength": 500
    },
    {
      "key": "legalStatus",
      "label": "当前法律状态",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "ownershipDescription",
      "label": "权利归属说明",
      "kind": "text",
      "maxLength": 10000
    },
    {
      "key": "patentScope",
      "label": "国内或国际",
      "kind": "text",
      "maxLength": 500,
      "options": [
        "国内",
        "国际"
      ]
    },
    {
      "key": "patentStatus",
      "label": "专利状态",
      "kind": "text",
      "maxLength": 500,
      "options": [
        "申请材料准备中",
        "已申请",
        "已受理",
        "已授权"
      ]
    },
    {
      "key": "publicationNumber",
      "label": "公开号",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "receiptDate",
      "label": "受理时间",
      "kind": "date",
      "maxLength": 500
    },
    {
      "key": "receiptNumber",
      "label": "受理号",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "remarks",
      "label": "备注",
      "kind": "text",
      "maxLength": 10000
    },
    {
      "key": "technicalField",
      "label": "技术领域",
      "kind": "text",
      "maxLength": 500
    }
  ],
  "COPYRIGHT": [
    {
      "key": "certificateDate",
      "label": "发证日期",
      "kind": "date",
      "maxLength": 500
    },
    {
      "key": "completionDate",
      "label": "开发完成日期",
      "kind": "date",
      "maxLength": 500
    },
    {
      "key": "copyrightOwnerList",
      "label": "著作权人及排序",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "developers",
      "label": "软件开发者",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "developmentLanguage",
      "label": "开发语言",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "developmentMode",
      "label": "开发方式",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "firstCompleter",
      "label": "第一完成人",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "firstCopyrightOwner",
      "label": "第一著作权人",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "firstPublicationDate",
      "label": "首次发表日期",
      "kind": "date",
      "maxLength": 500
    },
    {
      "key": "isPowerGridFirstCompleter",
      "label": "是否为广西电网第一完成人",
      "kind": "boolean",
      "maxLength": 500
    },
    {
      "key": "operatingPlatform",
      "label": "运行平台",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "registrationApplicationDate",
      "label": "登记申请日期",
      "kind": "date",
      "maxLength": 500
    },
    {
      "key": "registrationNumber",
      "label": "登记号",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "remarks",
      "label": "备注",
      "kind": "text",
      "maxLength": 10000
    },
    {
      "key": "rightsScope",
      "label": "权利范围",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "shortName",
      "label": "软件简称",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "softwareCategory",
      "label": "软件分类",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "softwareMainFunctions",
      "label": "主要功能",
      "kind": "text",
      "maxLength": 10000
    },
    {
      "key": "technicalFeatures",
      "label": "技术特点",
      "kind": "text",
      "maxLength": 10000
    },
    {
      "key": "version",
      "label": "版本号",
      "kind": "text",
      "maxLength": 500
    }
  ],
  "STANDARD": [
    {
      "key": "currentStage",
      "label": "标准当前阶段",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "draftCommitDate",
      "label": "送审稿提交时间",
      "kind": "date",
      "maxLength": 500
    },
    {
      "key": "draftSubmissionDate",
      "label": "送审稿形成时间",
      "kind": "date",
      "maxLength": 500
    },
    {
      "key": "drafters",
      "label": "主要起草人",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "leadingUnit",
      "label": "牵头单位",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "participatingUnits",
      "label": "参与单位",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "remarks",
      "label": "备注",
      "kind": "text",
      "maxLength": 10000
    },
    {
      "key": "responsibleOrganization",
      "label": "归口单位/标准组织",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "standardLevel",
      "label": "标准类型/级别",
      "kind": "text",
      "maxLength": 500
    }
  ],
  "TALENT": [
    {
      "key": "actualGraduationDate",
      "label": "实际毕业时间",
      "kind": "date",
      "maxLength": 500
    },
    {
      "key": "educationLevel",
      "label": "培养层次",
      "kind": "text",
      "maxLength": 500,
      "options": [
        "博士",
        "硕士"
      ]
    },
    {
      "key": "enrollmentDate",
      "label": "入学时间",
      "kind": "date",
      "maxLength": 500
    },
    {
      "key": "expectedGraduationDate",
      "label": "预计毕业时间",
      "kind": "date",
      "maxLength": 500
    },
    {
      "key": "remarks",
      "label": "备注",
      "kind": "text",
      "maxLength": 10000
    },
    {
      "key": "studentName",
      "label": "学生姓名",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "supervisorName",
      "label": "导师姓名",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "thesisTitle",
      "label": "学位论文题目",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "trainingStatus",
      "label": "当前培养状态",
      "kind": "text",
      "maxLength": 500
    },
    {
      "key": "trainingUnit",
      "label": "培养单位",
      "kind": "text",
      "maxLength": 500
    }
  ]
};
