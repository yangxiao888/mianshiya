package com.yangxiao.mianshiya.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yangxiao.mianshiya.common.ErrorCode;
import com.yangxiao.mianshiya.exception.ThrowUtils;
import com.yangxiao.mianshiya.model.dto.QuestionBankQuestion.QuestionBankQuestionQueryRequest;
import com.yangxiao.mianshiya.model.entity.Question;
import com.yangxiao.mianshiya.model.entity.QuestionBankQuestion;
import com.yangxiao.mianshiya.model.entity.User;
import com.yangxiao.mianshiya.model.vo.QuestionBankQuestionVO;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 题库题目关联表服务
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
public interface QuestionBankQuestionService extends IService<QuestionBankQuestion> {


    /**
     * 校验数据
     *
     * @param questionBankQuestion
     * @param add 对创建的数据进行校验
     */
    void validQuestionBankQuestion(QuestionBankQuestion questionBankQuestion, boolean add);

    /**
     * 获取查询条件
     *
     * @param questionBankQuestionQueryRequest
     * @return
     */
    QueryWrapper<QuestionBankQuestion> getQueryWrapper(QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest);

    /**
     * 获取题库题目关联表封装
     *
     * @param questionBankQuestion
     * @param request
     * @return
     */
    QuestionBankQuestionVO getQuestionBankQuestionVO(QuestionBankQuestion questionBankQuestion, HttpServletRequest request);

    /**
     * 分页获取题库题目关联表封装
     *
     * @param questionBankQuestionPage
     * @param request
     * @return
     */
    Page<QuestionBankQuestionVO> getQuestionBankQuestionVOPage(Page<QuestionBankQuestion> questionBankQuestionPage, HttpServletRequest request);


    /**
     * 批量向题库添加题目
     *
     * @param questionIdList
     * @param questionBankId
     * @param loginUser
     */
    @Transactional(rollbackFor = Exception.class)
    void batchAddQuestionToQuestionBank(List<Long> questionIdList, Long questionBankId, User loginUser);

    /**
     * 批量从题库移除题目
     *
     * @param questionIdList
     * @param questionBankId
     */
    @Transactional(rollbackFor = Exception.class)
    void batchremoveQuestionToQuestionBank(List<Long> questionIdList, Long questionBankId);


}
