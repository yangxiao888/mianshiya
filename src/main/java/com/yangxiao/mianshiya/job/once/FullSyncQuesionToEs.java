package com.yangxiao.mianshiya.job.once;


import com.yangxiao.mianshiya.esdao.QuestionEsDao;
import com.yangxiao.mianshiya.model.dto.question.QuestionEsDTO;
import com.yangxiao.mianshiya.model.entity.Question;
import com.yangxiao.mianshiya.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 题目全量同步到Es
 */
// todo 取消注释开启任务
//@Component
@Slf4j
public class FullSyncQuesionToEs implements CommandLineRunner {
    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionEsDao questionEsDao;


    @Override
    public void run(String... args){
    //获取所有题目（数据量不大的时候使用）
        List<Question> questionList = questionService.list();
        if(questionList.isEmpty()){
            return;
        }
        //转为Es实体类
        List<QuestionEsDTO> questionEsDTOList = questionList.stream()
                .map(QuestionEsDTO::objToDto)
                .collect(Collectors.toList());
        //批量存入Es中
        final int pageSize = 500;
        int total = questionEsDTOList.size();
        log.info("FullSyncQuesionEs start, total {}", total);
        for (int i = 0; i < total; i += pageSize) {
            //同步的数据下标不能超过总数据量
            int end = Math.min(i + pageSize, total);
                questionEsDao.saveAll(questionEsDTOList.subList(i, end));
        }
        log.info("FullSyncQuesionEs end, total {}", total);
    }
}
