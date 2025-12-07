package top.hanlin.publicipupload.service.impl;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.cvm.v20170312.CvmClient;
import com.tencentcloudapi.cvm.v20170312.models.DescribeInstancesRequest;
import com.tencentcloudapi.cvm.v20170312.models.DescribeInstancesResponse;
import top.hanlin.publicipupload.dao.FileOperationDao;
import top.hanlin.publicipupload.dao.impl.FileOperationDaoImpl;
import top.hanlin.publicipupload.service.TencentApiService;

public class TencentApiServiceImpl implements TencentApiService {
    FileOperationDao fileOperationDao=new FileOperationDaoImpl();
    @Override
    public boolean validateCredentials(String secretId,String secretKey) {
        try {
            // 初始化认证信息
            Credential cred = new Credential(secretId, secretKey);

            // 设置请求的地域
            String region = "ap-guangzhou";

            // 初始化 HTTP 配置
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("cvm.tencentcloudapi.com");

            // 初始化客户端配置
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);

            // 创建客户端
            CvmClient client = new CvmClient(cred, region, clientProfile);

            // 创建请求对象
            DescribeInstancesRequest req = new DescribeInstancesRequest();

            // 发送请求
            DescribeInstancesResponse resp = client.DescribeInstances(req);

            // 打印返回结果
            System.out.println(DescribeInstancesResponse.toJsonString(resp));
            return true;
        } catch (TencentCloudSDKException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean addIdAndKey(String name,String secretId, String secretKey) {
        return fileOperationDao.addIdAndKey(name,secretId,secretKey);
    }

    @Override
    public boolean deleteAccount(String provider, String secretId) {
        return fileOperationDao.deleteAccount(provider, secretId);
    }
}
