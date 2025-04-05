package com.sky.test;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class HttpClientTest {

    /**
     * 测试通过httpclient发送GET方式的请求
     */
    @Test
    public void testGet() throws Exception {
        //创建httpclient对象
        CloseableHttpClient HttpClient = HttpClients.createDefault();

        //创建请求对象
        HttpGet httpGet = new HttpGet("http://localhost:8080/user/shop/status");

        //发送请求
        CloseableHttpResponse response = HttpClient.execute(httpGet);

        //获取服务端返回的状态码
        int statusCode = response.getStatusLine().getStatusCode();
        System.out.println("服务器返回的状态码为: " + statusCode);

        HttpEntity entity = response.getEntity();
        String body = EntityUtils.toString(entity);
        System.out.println("服务端返回的数据为: " + body);

        //关闭资源
        response.close();
        HttpClient.close();
    }


    /*
     * 测试通过httpclient发送POST方式的请求
     * */
    @Test
    public void testPost() throws Exception {
        //创建Http请求对象
        CloseableHttpClient HttpClient = HttpClients.createDefault();

        //创建请求对象
        HttpPost httpPost = new HttpPost("http://localhost:8080/admin/employee/login");

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("username", "admin");
        jsonObject.put("password", "123456");

        StringEntity stringEntity = new StringEntity(jsonObject.toString());
        //指定请求编码方式
        stringEntity.setContentEncoding("UTF-8");
        //数据格式
        stringEntity.setContentType("application/json");
        httpPost.setEntity(stringEntity);

        //发送请求
        CloseableHttpResponse response = HttpClient.execute(httpPost);

        //解析返回结果
        int statusCode = response.getStatusLine().getStatusCode();
        System.out.println(statusCode);

        HttpEntity entity = response.getEntity();
        String body = EntityUtils.toString(entity);
        System.out.println("响应数据为：" + body);
        //关闭资源
        HttpClient.close();
        response.close();
    }

}
