package com.yangxiao.mianshiya.model.dto.QuestionBankQuestion;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class QuestionBankQuestionBatchRemoRequest implements Serializable {


    /**
     * 题库 id
     */
    private Long questionBankId;

    /**
     * 题目 id 列表
     */
    private List<Long> questionIdList;

    private static final long serialVersionUID = 1L;
}
