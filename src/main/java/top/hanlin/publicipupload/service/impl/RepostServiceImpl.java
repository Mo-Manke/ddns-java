package top.hanlin.publicipupload.service.impl;

import top.hanlin.publicipupload.dao.FileOperationDao;

import top.hanlin.publicipupload.dao.impl.FileOperationDaoImpl;
import top.hanlin.publicipupload.entity.UserInfo;
import top.hanlin.publicipupload.service.RepostService;

import java.util.List;

public class RepostServiceImpl implements RepostService {
    FileOperationDao repostDao=new FileOperationDaoImpl();
    @Override
    public boolean login(String password) {
        String results=repostDao.getPassword();
        if(!(results == null ||results.isEmpty())){
            return password.equals(results);
        }
        return false;
    }

    @Override
    public List<UserInfo> getAllUser() {
        return repostDao.getAllUser();
    }

    @Override
    public void modifyPassword(String modify) {
        repostDao.modifyPassword(modify);
    }
    
    @Override
    public boolean isInitialPassword() {
        return repostDao.isInitialPassword();
    }
}
