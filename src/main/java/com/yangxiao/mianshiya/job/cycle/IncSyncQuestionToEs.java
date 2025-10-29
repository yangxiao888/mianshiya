package com.yangxiao.mianshiya.job.cycle;

import com.yangxiao.mianshiya.esdao.QuestionEsDao;
import com.yangxiao.mianshiya.mapper.QuestionMapper;
import com.yangxiao.mianshiya.model.dto.question.QuestionEsDTO;
import com.yangxiao.mianshiya.model.entity.Question;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 增量题目同步到Es
 */
@Component
@Slf4j
public class IncSyncQuestionToEs {
    @Resource
    private QuestionEsDao questionEsDao;

    @Resource
    private QuestionMapper questionMapper;

    /**
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60 * 1000)
    public void run() {
        //查询近5分钟的数据
        Date date = new Date(new Date().getTime() - 5 * 60 * 1000);
        List<Question> questionList = questionMapper.listQuestionWithDelete(date);
        if (questionList.isEmpty()) {
            log.info("no inc question");
            return;
        }
        //转化为QuestionEsDTO
        List<QuestionEsDTO> questionEsDTOList = questionList.stream()
                .map(QuestionEsDTO::objToDto)
                .collect(Collectors.toList());
        //批量存入Es中
        final int pageSize = 500;
        int total = questionEsDTOList.size();
        log.info("IncSyncQuesionEs start, total {}", total);
        for (int i = 0; i < total; i += pageSize) {
            //同步的数据下标不能超过总数据量
            int end = Math.min(i + pageSize, total);
            questionEsDao.saveAll(questionEsDTOList.subList(i, end));
        }
        log.info("IncSyncQuesionEs end, total {}", total);


    }

}
