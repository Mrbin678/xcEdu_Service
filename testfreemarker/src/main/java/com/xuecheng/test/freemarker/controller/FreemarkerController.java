package com.xuecheng.test.freemarker.controller;

import com.xuecheng.test.freemarker.model.Student;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * @Author: 98050
 * @Time: 2019-03-28 14:27
 * @Feature:
 */
@RequestMapping("/freemarker")
@Controller
public class FreemarkerController {


    @RequestMapping("/test1")
    public String freemarker(Map<String,Object> map){
        //向数据模型放数据
        map.put("name","李四");
        Student stu1 = new Student();
        stu1.setName("小明");
        stu1.setAge(18);
        stu1.setMondy(1000.86f);
        stu1.setBirthday(new Date());
        Student stu2 = new Student();
        stu2.setName("小红");
        stu2.setMondy(200.1f);
        stu2.setAge(19);
        stu2.setBirthday(new Date());
        List<Student> friends = new ArrayList<>();
        friends.add(stu1);
        stu2.setFriends(friends);
        stu2.setBestFriend(stu1);
        List<Student> stus = new ArrayList<>();
        stus.add(stu1);
        stus.add(stu2);
        //向数据模型放数据
        map.put("stus",stus);
        //准备map数据
        HashMap<String,Student> stuMap = new HashMap<>();
        stuMap.put("stu1",stu1);
        stuMap.put("stu2",stu2);
        //向数据模型放数据
        map.put("stu1",stu1);
        //向数据模型放数据
        map.put("stuMap",stuMap);
        //返回模板文件名称
        return "test1";
    }

    @Autowired
    RestTemplate restTemplate;

    @RequestMapping("/banner")
    public String index_banner(Map<String,Object> map){
        String dataUrl = "http://localhost:31001/cms/config/getModel/5a791725dd573c3574ee333f";
        ResponseEntity<Map> entity = restTemplate.getForEntity(dataUrl, Map.class);
        Map body = entity.getBody();
        System.out.println(body);
        if (body != null) {
            map.putAll(body);
        }
        return "index_banner";
    }

    @RequestMapping("/course")
    public String course(Map<String,Object> map){
        String dataUrl = "http://localhost:31200/course/courseview/4028e58161bd3b380161bd3bcd2f0000";
        ResponseEntity<Map> entity = restTemplate.getForEntity(dataUrl,Map.class);
        Map body = entity.getBody();
        map.putAll(body);
        return "course";
    }
}
