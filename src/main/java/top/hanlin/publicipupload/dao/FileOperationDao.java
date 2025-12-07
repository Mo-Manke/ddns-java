package top.hanlin.publicipupload.dao;

import top.hanlin.publicipupload.entity.UserInfo;

import java.util.List;

public interface FileOperationDao {
    String getPassword();
    List<UserInfo> getAllUser();
    boolean addIdAndKey(String name,String secretId,String secretKey);
    boolean deleteAccount(String provider, String secretId);
    void modifyPassword(String modify);
    boolean isInitialPassword();
}
