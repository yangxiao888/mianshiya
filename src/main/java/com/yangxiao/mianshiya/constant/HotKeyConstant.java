package com.yangxiao.mianshiya.constant;

/**
 * HotKey常量
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
public interface HotKeyConstant {

    /**
     * 热Key的前缀
     */

    String HOTKEY_Bank_PREFIX = "bank_detail_";
    String HOTKEY_Question_PREFIX = "question_detail_";


    /**
     * 获取题库热key
     * @param id
     * @return
     */
    static String getBankHotkey(Long id){
        return HOTKEY_Bank_PREFIX+id;
    }
    /**
     * 获取题目热key
     * @param id
     * @return
     */
    static String getQuestionHotkey(Long id){
        return HOTKEY_Question_PREFIX+id;
    }
    
}

