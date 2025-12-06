package top.hanlin.publicipupload.service;

import top.hanlin.publicipupload.entity.UserInfo;

import java.util.List;

public interface RepostService {
    /**
     *
     * @param password 密码
     * @return 返回密码校验结果
     */
    boolean login(String password);

    /**
     *
     * @return 获取所有用户
     */
    List<UserInfo> getAllUser();

    void modifyPassword(String modify);
    
    /**
     * 判断是否是初始密码
     */
    boolean isInitialPassword();
}
