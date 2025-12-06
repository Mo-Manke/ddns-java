package top.hanlin.publicipupload.dao.impl;

import top.hanlin.publicipupload.dao.FileOperationDao;
import top.hanlin.publicipupload.entity.UserInfo;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileOperationDaoImpl implements FileOperationDao {
    @Override
    public List<UserInfo> getAllUser() {
        List<UserInfo> userList = new ArrayList<>();

        File[] cloudFolders = new File[]{
                new File("腾讯云"),
                new File("阿里云")
        };

        for (File cloudFolder : cloudFolders) {
            // 获取该云平台下的所有子文件夹（代表用户 id）
            File[] subFolders = cloudFolder.listFiles(File::isDirectory);

            if (subFolders != null) {
                for (File subFolder : subFolders) {
                    // 获取每个 id 文件夹下的文件（代表 key）
                    File[] files = subFolder.listFiles(File::isFile);

                    if (files != null) {
                        for (File file : files) {
                            // 有文件才记录
                            userList.add(new UserInfo(
                                    cloudFolder.getName(),   // name: 所属云平台
                                    subFolder.getName(),     // id: 子文件夹名称
                                    file.getName()           // key: 文件名
                            ));
                        }
                    }
                }
            }
        }
        return userList;
    }

    @Override
    public boolean addIdAndKey(String name,String secretId, String secretKey) {
        File tencentFolder = new File(name);

        // 检查腾讯云目录是否存在
        if (!tencentFolder.exists() || !tencentFolder.isDirectory()) {
            System.out.println(name+"文件夹不存在或不是一个目录");
            return false;
        }

        // 创建 secretId 文件夹路径
        File secretIdFolder = new File(tencentFolder, secretId);

        // 判断 id 是否已经存在
        if (secretIdFolder.exists()) {
            System.out.println(secretId + " 文件夹已存在，不添加");
            return false;
        }

        // 如果文件夹不存在，则创建
        boolean isCreated = secretIdFolder.mkdir(); // 创建单层目录
        if (!isCreated) {
            System.out.println(secretId + " 文件夹创建失败");
            return false;
        }

        // 创建 secretKey 文件
        File secretKeyFile = new File(secretIdFolder, secretKey);

        try {
            // 如果文件已存在，不重复创建
            if (!secretKeyFile.exists()) {
                isCreated = secretKeyFile.createNewFile();
                if (!isCreated) {
                    System.out.println(secretKey + " 文件创建失败");
                    return false;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true; // 成功返回 true
    }

    @Override
    public void modifyPassword(String modify) {
        try (FileWriter writer = new FileWriter("password")) {
            writer.write(modify);
            System.out.println("密码文件更新成功！");
        } catch (IOException e) {
            System.err.println("写入密码文件失败：" + e.getMessage());
        }
    }


    @Override
    public String getPassword() {
        File file = new File("password"); // 根目录下的 password 文件

        // 如果文件不存在，则创建新文件并写入默认内容
        if (!file.exists()) {
            createCloudFolders();
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("123456");
            } catch (IOException e) {
                e.printStackTrace();
                return ""; // 创建失败时返回空字符串
            }
        }

        // 读取文件内容
        StringBuilder content = new StringBuilder();
        try (FileReader reader = new FileReader(file)) {
            int c;
            while ((c = reader.read()) != -1) {
                content.append((char) c);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
        System.out.println(content.toString());
        return content.toString();
    }
    private void createCloudFolders() {
        // 创建 "腾讯云" 文件夹
        File tencentFolder = new File("腾讯云");
        if (!tencentFolder.exists()) {
            boolean isCreated = tencentFolder.mkdir(); // 创建单层目录
            if (!isCreated) {
                System.out.println("腾讯云 文件夹创建失败");
            }
        }

        // 创建 "阿里云" 文件夹
        File aliyunFolder = new File("阿里云");
        if (!aliyunFolder.exists()) {
            boolean isCreated = aliyunFolder.mkdir(); // 创建单层目录
            if (!isCreated) {
                System.out.println("阿里云 文件夹创建失败");
            }
        }
    }
    
    @Override
    public boolean isInitialPassword() {
        return "123456".equals(getPassword());
    }
}
