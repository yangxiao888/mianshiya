package com.yangxiao.mianshiya.mapper;

import java.util.Date;
import java.util.List;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangxiao.mianshiya.model.entity.Question;

/**
 * @author yang
 * @description 针对表【question(题目)】的数据库操作Mapper
 * @createDate 2025-10-25 14:16:29
 * @Entity generator.domain.Question
 */
public interface QuestionMapper extends BaseMapper<Question> {
    /**
     * 用于查询修改的数据（包括删除）
     */
    List<Question> listQuestionWithDelete(Date date);

}




