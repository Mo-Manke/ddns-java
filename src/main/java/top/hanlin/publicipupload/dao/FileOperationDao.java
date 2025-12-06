package top.hanlin.publicipupload.dao;

import top.hanlin.publicipupload.entity.UserInfo;

import java.util.List;

public interface FileOperationDao {
    String getPassword();
    List<UserInfo> getAllUser();
    boolean addIdAndKey(String name,String secretId,String secretKey);
    void modifyPassword(String modify);
    boolean isInitialPassword();
}
