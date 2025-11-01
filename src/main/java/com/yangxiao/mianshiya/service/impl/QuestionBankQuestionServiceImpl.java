package com.yangxiao.mianshiya.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yangxiao.mianshiya.common.ErrorCode;
import com.yangxiao.mianshiya.constant.CommonConstant;
import com.yangxiao.mianshiya.exception.BusinessException;
import com.yangxiao.mianshiya.exception.ThrowUtils;
import com.yangxiao.mianshiya.mapper.QuestionBankQuestionMapper;
import com.yangxiao.mianshiya.model.dto.QuestionBankQuestion.QuestionBankQuestionQueryRequest;
import com.yangxiao.mianshiya.model.entity.Question;
import com.yangxiao.mianshiya.model.entity.QuestionBank;
import com.yangxiao.mianshiya.model.entity.QuestionBankQuestion;
import com.yangxiao.mianshiya.model.entity.User;
import com.yangxiao.mianshiya.model.vo.QuestionBankQuestionVO;
import com.yangxiao.mianshiya.model.vo.UserVO;
import com.yangxiao.mianshiya.service.QuestionBankQuestionService;
import com.yangxiao.mianshiya.service.QuestionBankService;
import com.yangxiao.mianshiya.service.QuestionService;
import com.yangxiao.mianshiya.service.UserService;
import com.yangxiao.mianshiya.utils.SqlUtils;
import io.github.classgraph.utils.Join;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.aspectj.weaver.ast.Var;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 题库题目关联表服务实现
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class QuestionBankQuestionServiceImpl extends ServiceImpl<QuestionBankQuestionMapper, QuestionBankQuestion> implements QuestionBankQuestionService {

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private QuestionService questionService;

    @Resource
    private QuestionBankService questionBankService;

    /**
     * 校验数据
     *
     * @param questionBankQuestion
     * @param add                  对创建的数据进行校验
     */
    @Override
    public void validQuestionBankQuestion(QuestionBankQuestion questionBankQuestion, boolean add) {
        ThrowUtils.throwIf(questionBankQuestion == null, ErrorCode.PARAMS_ERROR);
        //题目和题库必须存在
        Long questionBankId = questionBankQuestion.getQuestionBankId();
        if (questionBankId != null) {
            QuestionBank questionBank = questionBankService.getById(questionBankId);
            ThrowUtils.throwIf(questionBank == null, ErrorCode.PARAMS_ERROR, "题库不存在");
        }

        Long questionId = questionBankQuestion.getQuestionId();
        if (questionId != null) {
            Question question = questionService.getById(questionId);
            ThrowUtils.throwIf(question == null, ErrorCode.PARAMS_ERROR, "题目不存在");
        }
    }


    /**
     * 获取查询条件
     *
     * @param questionBankQuestionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<QuestionBankQuestion> getQueryWrapper(QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest) {
        QueryWrapper<QuestionBankQuestion> queryWrapper = new QueryWrapper<>();
        if (questionBankQuestionQueryRequest == null) {
            return queryWrapper;
        }
        // todo 从对象中取值
        Long id = questionBankQuestionQueryRequest.getId();
        Long notId = questionBankQuestionQueryRequest.getNotId();
        String sortField = questionBankQuestionQueryRequest.getSortField();
        String sortOrder = questionBankQuestionQueryRequest.getSortOrder();
        Long questionId = questionBankQuestionQueryRequest.getQuestionId();
        Long questionBankId = questionBankQuestionQueryRequest.getQuestionBankId();
        Long userId = questionBankQuestionQueryRequest.getUserId();
        // todo 补充需要的查询条件

        // 精确查询
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionId), "questionId", questionId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionBankId), "questionBankId", questionBankId);
        // 排序规则
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    /**
     * 获取题库题目关联表封装
     *
     * @param questionBankQuestion
     * @param request
     * @return
     */
    @Override
    public QuestionBankQuestionVO getQuestionBankQuestionVO(QuestionBankQuestion questionBankQuestion, HttpServletRequest request) {
        // 对象转封装类
        QuestionBankQuestionVO questionBankQuestionVO = QuestionBankQuestionVO.objToVo(questionBankQuestion);

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Long userId = questionBankQuestion.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        questionBankQuestionVO.setUser(userVO);
        // endregion

        return questionBankQuestionVO;
    }

    /**
     * 分页获取题库题目关联表封装
     *
     * @param questionBankQuestionPage
     * @param request
     * @return
     */
    @Override
    public Page<QuestionBankQuestionVO> getQuestionBankQuestionVOPage(Page<QuestionBankQuestion> questionBankQuestionPage, HttpServletRequest request) {
        List<QuestionBankQuestion> questionBankQuestionList = questionBankQuestionPage.getRecords();
        Page<QuestionBankQuestionVO> questionBankQuestionVOPage = new Page<>(questionBankQuestionPage.getCurrent(), questionBankQuestionPage.getSize(), questionBankQuestionPage.getTotal());
        if (CollUtil.isEmpty(questionBankQuestionList)) {
            return questionBankQuestionVOPage;
        }
        // 对象列表 => 封装对象列表
        List<QuestionBankQuestionVO> questionBankQuestionVOList = questionBankQuestionList.stream().map(questionBankQuestion -> {
            return QuestionBankQuestionVO.objToVo(questionBankQuestion);
        }).collect(Collectors.toList());

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Set<Long> userIdSet = questionBankQuestionList.stream().map(QuestionBankQuestion::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 填充信息
        questionBankQuestionVOList.forEach(questionBankQuestionVO -> {
            Long userId = questionBankQuestionVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            questionBankQuestionVO.setUser(userService.getUserVO(user));
        });
        // endregion

        questionBankQuestionVOPage.setRecords(questionBankQuestionVOList);
        return questionBankQuestionVOPage;
    }

    /**
     * 批量向题库添加题目
     *
     * @param questionIdList
     * @param questionBankId
     * @param loginUser
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAddQuestionToQuestionBank(List<Long> questionIdList, Long questionBankId, User loginUser) {
        //参数校验
        ThrowUtils.throwIf(questionIdList == null, ErrorCode.PARAMS_ERROR, "题目列表为空");
        ThrowUtils.throwIf(questionBankId == null || questionBankId <= 0, ErrorCode.PARAMS_ERROR, "题库id非法");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        //验证题目id是否存在
        /*List<Question> questionList = questionService.listByIds(questionIdList);
        List<Long> validQuesionIdList = questionList.stream()
                .map(Question::getId)
                .collect(Collectors.toList());*/
        //SQL优化——防止使用Select * ,减少返回包装
        LambdaQueryWrapper<Question> questionLambdaQueryWrapper = Wrappers.lambdaQuery(Question.class)
                .select(Question::getId)
                .in(Question::getId, questionIdList);
        List<Long> validQuesionIdList = questionService.listObjs(questionLambdaQueryWrapper, obj -> (Long) obj);

        ThrowUtils.throwIf(CollUtil.isEmpty(validQuesionIdList), ErrorCode.PARAMS_ERROR, "合法的题目列表为空");

        //验证题库id是否存在
        QuestionBank questionBank = questionBankService.getById(questionBankId);
        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR, "题库id不存在");

        //检验哪些题目已经存在题库之中
        LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                .eq(QuestionBankQuestion::getQuestionBankId, questionBankId)
                .in(QuestionBankQuestion::getQuestionId, validQuesionIdList);
        List<QuestionBankQuestion> existQuestionList = this.list(lambdaQueryWrapper);
        //已经在题库中存在的题目
        Set<Long> questionIds = existQuestionList.stream()
                .map(QuestionBankQuestion::getQuestionId)
                .collect(Collectors.toSet());
        //需要插入的题    目
        validQuesionIdList = validQuesionIdList.stream()
                .filter(questionId -> !questionIds.contains(questionId)).collect(Collectors.toList());
        ThrowUtils.throwIf(CollUtil.isEmpty(validQuesionIdList), ErrorCode.PARAMS_ERROR, "所有题目都存在于题库中");


        //并发编程
        // 自定义线程池
        ThreadPoolExecutor customExecutor = new ThreadPoolExecutor(
                20,                         // 核心线程数
                50,                        // 最大线程数
                60L,                       // 线程空闲存活时间
                TimeUnit.SECONDS,           // 存活时间单位
                new LinkedBlockingQueue<>(1000),  // 阻塞队列容量
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：由调用线程处理任务
        );
        //保存所有批次的CompletableFuture
        ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();

        //执行分批事务插入
        int batchSize = 1000;
        int totalQuestionBatchSize = validQuesionIdList.size();
        for (int i = 0; i < totalQuestionBatchSize; i += batchSize) {
            //生成每批插入的数据
            List<Long> batchValidQuesionIdList = validQuesionIdList.subList(i, Math.min(i + batchSize, validQuesionIdList.size()));
            List<QuestionBankQuestion> questionbankQuestionList = batchValidQuesionIdList.stream()
                    .map(questionId -> {
                        QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
                        questionBankQuestion.setQuestionId(questionId);
                        questionBankQuestion.setQuestionBankId(questionBankId);
                        questionBankQuestion.setUserId(loginUser.getId());
                        return questionBankQuestion;
                    }).collect(Collectors.toList());
            //使用事务批量处理每批数据（Spring中的事务依赖于代理机制，不能用this去调用分批事务的方法）
            QuestionBankQuestionServiceImpl questionBankQuestionServiceImpl = (QuestionBankQuestionServiceImpl) AopContext.currentProxy();

            //异步处处理，添加到自定义线程池
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                questionBankQuestionServiceImpl.batchAddQuestionToBankInner(questionbankQuestionList);
            }, customExecutor);//使用自定义线程池执行
            //添加到列表，方便后续管理
            futures.add(future);
        }

        //等待所有批次完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        //关闭线程池
        customExecutor.shutdown();
        /*for (Long questionId : validQuesionIdList) {
            QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
            questionBankQuestion.setQuestionId(questionId);
            questionBankQuestion.setQuestionBankId(questionBankId);
            questionBankQuestion.setUserId(loginUser.getId());

            try {
                boolean result = this.save(questionBankQuestion);
                if (!result) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "向题库添加题目失败");
                }
            } catch (DataIntegrityViolationException e) {
                log.error("数据库唯一键冲突或违反其他完整性约束，题目 id: {}, 题库 id: {}, 错误信息: {}",
                        questionId, questionBankId, e.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目已存在于该题库，无法重复添加");
            } catch (DataAccessException e) {
                log.error("数据库连接问题、事务问题等导致操作失败，题目 id: {}, 题库 id: {}, 错误信息: {}",
                        questionId, questionBankId, e.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据库操作失败");
            } catch (Exception e) {
                // 捕获其他异常，做通用处理
                log.error("添加题目到题库时发生未知错误，题目 id: {}, 题库 id: {}, 错误信息: {}",
                        questionId, questionBankId, e.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
            }
        }*/
    }

    /**
     * 使用分批事务批量添加题目（仅内部调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchAddQuestionToBankInner(List<QuestionBankQuestion> questionbankQuestionList) {
        //批量操作
        try {
            boolean result = this.saveBatch(questionbankQuestionList);
            if (!result) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "向题库添加题目失败");
            }
        } catch (DataIntegrityViolationException e) {
            log.error("数据库唯一键冲突或违反其他完整性约束， 错误信息: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目已存在于该题库，无法重复添加");
        } catch (DataAccessException e) {
            log.error("数据库连接问题、事务问题等导致操作失败，错误信息: {}",e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据库操作失败");
        } catch (Exception e) {
            // 捕获其他异常，做通用处理
            log.error("添加题目到题库时发生未知错误，错误信息: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
        }

    }


    /**
     * 批量从题库移除题目
     *
     * @param questionIdList
     * @param questionBankId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchremoveQuestionToQuestionBank(List<Long> questionIdList, Long questionBankId) {
        //参数校验
        ThrowUtils.throwIf(questionIdList == null, ErrorCode.PARAMS_ERROR, "题目列表为空");
        ThrowUtils.throwIf(questionBankId == null || questionBankId <= 0, ErrorCode.PARAMS_ERROR, "题库id非法");

        //检验哪些题目已经存在与题库之中
        LambdaQueryWrapper<QuestionBankQuestion> queryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                .eq(QuestionBankQuestion::getQuestionBankId, questionBankId)
                .in(QuestionBankQuestion::getQuestionId, questionIdList);
        List<QuestionBankQuestion> questionBankQuestionList = this.list(queryWrapper);
        ThrowUtils.throwIf(CollUtil.isEmpty(questionBankQuestionList), ErrorCode.PARAMS_ERROR, "题目不在该题库");

        //执行删除
        for (QuestionBankQuestion questionBankQuestion : questionBankQuestionList) {

            try {
                boolean result = this.removeById(questionBankQuestion.getId());
                if (!result) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量从题库移除题目失败");
                }
            } catch (DataAccessException e) {
                log.error("数据库连接问题、事务问题等导致操作失败，题目 id: {}, 题库 id: {}, 错误信息: {}",
                        questionBankQuestion.getQuestionId(), questionBankId, e.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据库操作失败");
            } catch (Exception e) {
                // 捕获其他异常，做通用处理
                log.error("批量从题库移除题目时发生未知错误，题目 id: {}, 题库 id: {}, 错误信息: {}",
                        questionBankQuestion.getQuestionId(), questionBankId, e.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量从题库移除题目失败");
            }
        }

        //执行移除
        /*
        for (Long questionId : questionIdList) {
            LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                    .eq(QuestionBankQuestion::getQuestionId, questionId)
                    .eq(QuestionBankQuestion::getQuestionBankId, questionBankId);
            boolean result = this.remove(lambdaQueryWrapper);
            if (!result) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "从题库移除题目失败");
            }
        }*/

    }

}