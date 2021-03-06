package com.xuecheng.managecourse.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.xuecheng.framework.domain.cms.CmsPage;
import com.xuecheng.framework.domain.cms.response.CmsPageResult;
import com.xuecheng.framework.domain.cms.response.CmsPostPageResult;
import com.xuecheng.framework.domain.course.*;
import com.xuecheng.framework.domain.course.ext.CourseInfo;
import com.xuecheng.framework.domain.course.ext.CourseView;
import com.xuecheng.framework.domain.course.ext.TeachplanNode;
import com.xuecheng.framework.domain.course.request.CourseListRequest;
import com.xuecheng.framework.domain.course.response.*;
import com.xuecheng.framework.exception.ExceptionCast;
import com.xuecheng.framework.model.response.CommonCode;
import com.xuecheng.framework.model.response.QueryResponseResult;
import com.xuecheng.framework.model.response.QueryResult;
import com.xuecheng.framework.model.response.ResponseResult;
import com.xuecheng.managecourse.client.CmsPageClient;
import com.xuecheng.managecourse.dao.*;
import com.xuecheng.managecourse.service.CourseService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * @Author: 98050
 * @Time: 2019-04-03 22:57
 * @Feature:
 */
@Service
public class CourseServiceImpl implements CourseService {

    private final TeachPlanMapper teachPlanMapper;

    private final CourseBaseRepository courseBaseRepository;

    private final TeachPlanRepository teachPlanRepository;

    private final CourseMapper courseMapper;

    private final CourseMarketRepository courseMarketRepository;

    private final CoursePicRepository coursePicRepository;

    private final CmsPageClient cmsPageClient;

    private final CoursePubRepository coursePubRepository;

    @Value("${course‐publish.dataUrlPre}")
    private String publish_dataUrlPre;
    @Value("${course‐publish.pagePhysicalPath}")
    private String publish_page_physicalPath;
    @Value("${course‐publish.pageWebPath}")
    private String publish_page_webPath;
    @Value("${course‐publish.siteId}")
    private String publish_siteId;
    @Value("${course‐publish.templateId}")
    private String publish_templateId;
    @Value("${course‐publish.previewUrl}")
    private String previewUrl;


    @Autowired
    public CourseServiceImpl(TeachPlanMapper teachPlanMapper, CourseBaseRepository courseBaseRepository, TeachPlanRepository teachPlanRepository, CourseMapper courseMapper, CourseMarketRepository courseMarketRepository, CoursePicRepository coursePicRepository, CmsPageClient cmsPageClient, CoursePubRepository coursePubRepository) {
        this.teachPlanMapper = teachPlanMapper;
        this.courseBaseRepository = courseBaseRepository;
        this.teachPlanRepository = teachPlanRepository;
        this.courseMapper = courseMapper;
        this.courseMarketRepository = courseMarketRepository;
        this.coursePicRepository = coursePicRepository;
        this.cmsPageClient = cmsPageClient;
        this.coursePubRepository = coursePubRepository;
    }

    /**
     * 课程计划查询
     * @param courseId
     * @return
     */
    @Override
    public TeachplanNode findTeachplanList(String courseId) {
        return teachPlanMapper.selectList(courseId);
    }

    /**
     * 课程计划添加
     * 说明：如果当前teachplan对象中parentId为空，说明这是一个二级节点，那么parentId应该为根节点，也就是一级节点
     * @param teachplan
     * @return
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED,rollbackFor = Exception.class)
    public TeachPlanResult add(Teachplan teachplan) {
        //1.校验参数对象
        if (teachplan == null) {
            ExceptionCast.cast(CourseCode.COURSE_PLAN_ADD_PARAMETERISNULL);
        }
        //2.取出课程id
        String courseId = teachplan.getCourseid();
        if (StringUtils.isEmpty(courseId)) {
            ExceptionCast.cast(CourseCode.COURSE_PLAN_ADD_COURSEIDISNULL);
        }
        //3.根据课程id和课程计划名称进行校验
        List<Teachplan> list = this.teachPlanRepository.findByCourseidAndPname(courseId, teachplan.getPname());
        if (list.size() != 0){
            ExceptionCast.cast(CourseCode.COURSE_PLAN_ADD_PLANNAMEISEXISTS);
        }
        //4.取出父节点id
        String parentId = teachplan.getParentid();
        if (StringUtils.isEmpty(parentId)) {
            parentId = getTeachPlanRoot(courseId);
        }
        //5.获取父节点信息
        Optional<Teachplan> optional = this.teachPlanRepository.findById(parentId);
        if (!optional.isPresent()) {
            ExceptionCast.cast(CourseCode.COURSE_PLAN_ADD_PARENTNODEISNULL);
        }
        Teachplan parentNode = optional.get();
        //6.设置父节点
        teachplan.setParentid(parentId);
        //7.未发布
        teachplan.setStatus("0");
        //8.设置子节点的级别，根据父节点来判断
        String level1 = "1";
        String level2 = "2";
        String level3 = "3";
        if (level1.equals(parentNode.getGrade())) {
            teachplan.setGrade(level2);
        } else if (level2.equals(parentNode.getGrade())) {
            teachplan.setGrade(level3);
        }
        //9.设置课程id
        teachplan.setCourseid(parentNode.getCourseid());
        //10.保存
        Teachplan save = this.teachPlanRepository.save(teachplan);
        return new TeachPlanResult(CommonCode.SUCCESS, save);
    }

    /**
     * 课程计划修改
     * @param teachplan
     * @return
     */
    @Override
    public TeachPlanResult edit(Teachplan teachplan) {
        this.teachPlanRepository.save(teachplan);
        return null;
    }

    @Override
    public ResponseResult delete(String id) {
        return null;
    }

    /**
     * 分页查询课程
     * @param page
     * @param size
     * @param courseListRequest
     * @return
     */
    @Override
    public QueryResponseResult queryByPage(int page, int size, CourseListRequest courseListRequest) {
        //1.分页
        PageHelper.startPage(page,size);
        //2.构建查询条件
        if (StringUtils.isEmpty(courseListRequest.getCompanyId())){
            ExceptionCast.cast(CourseCode.COURSE_COMPANYISNULL);
        }
        Page<CourseInfo> courseInfos = this.courseMapper.findCourseListPage(courseListRequest);
        List<CourseInfo> list = courseInfos.getResult();
        QueryResult<CourseInfo> queryResult = new QueryResult<>();
        queryResult.setTotal(courseInfos.getTotal());
        queryResult.setList(list);
        return new QueryResponseResult(CommonCode.SUCCESS,queryResult);
    }

    /**
     * 课程添加
     * @param courseBase
     * @return
     */
    @Override
    public AddCourseResult courseAdd(CourseBase courseBase) {
        if (courseBase == null){
            ExceptionCast.cast(CourseCode.COURSE_ADD_PARAMTERISNULL);
        }
        CourseBase save = this.courseBaseRepository.save(courseBase);
        return new AddCourseResult(CommonCode.SUCCESS, save.getId());
    }

    /**
     * 根据id查询课程信息
     * @param id
     * @return
     */
    @Override
    public CourseBase getCourseById(String id) {
        Optional<CourseBase> optional = this.courseBaseRepository.findById(id);
        if (!optional.isPresent()){
            ExceptionCast.cast(CourseCode.COURSE_GET_ISNULL);
        }
        return optional.get();
    }

    /**
     * 修改课程信息
     * @param courseBase
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class,propagation = Propagation.REQUIRED)
    public UpdateCourseResult courseUpdate(CourseBase courseBase) {
        if (courseBase == null){
            ExceptionCast.cast(CourseCode.COURSE_GET_ISNULL);
        }
        Optional<CourseBase> optional = this.courseBaseRepository.findById(courseBase.getId());
        if (!optional.isPresent()){
            ExceptionCast.cast(CourseCode.COURSE_GET_ISNULL);
        }
        CourseBase update = optional.get();
        if (StringUtils.isEmpty(courseBase.getCompanyId())){
            ExceptionCast.cast(CourseCode.COURSE_COMPANYISNULL);
        }else {
            update.setCompanyId(courseBase.getCompanyId());
        }
        if (StringUtils.isEmpty(courseBase.getDescription())){
            ExceptionCast.cast(CourseCode.COURSE_DESCRIPTION_ISNULL);
        }else {
            update.setDescription(courseBase.getDescription());
        }
        if (StringUtils.isEmpty(courseBase.getGrade())){
            ExceptionCast.cast(CourseCode.COURSE_GRADE_ISNULL);
        }else {
            update.setGrade(courseBase.getGrade());
        }
        if (StringUtils.isEmpty(courseBase.getMt())){
            ExceptionCast.cast(CourseCode.COURSE_MT_ISNULL);
        }else {
            update.setMt(courseBase.getMt());
        }
        if (StringUtils.isEmpty(courseBase.getName())){
            ExceptionCast.cast(CourseCode.COURSE_NAME_ISNULL);
        }else {
            update.setName(courseBase.getName());
        }
        if (StringUtils.isEmpty(courseBase.getSt())){
            ExceptionCast.cast(CourseCode.COURSE_ST_ISNULL);
        }else {
            update.setSt(courseBase.getSt());
        }
        if (StringUtils.isEmpty(courseBase.getStatus())){
            ExceptionCast.cast(CourseCode.COURSE_STATUS_ISNULL);
        }else {
            update.setStatus(courseBase.getStatus());
        }
        if (StringUtils.isEmpty(courseBase.getStudymodel())){
            ExceptionCast.cast(CourseCode.COURSE_STUDYMODEL_ISNULL);
        }else {
            update.setStudymodel(courseBase.getStudymodel());
        }
//        if (StringUtils.isEmpty(courseBase.getTeachmode())){
//            ExceptionCast.cast(CourseCode.COURSE_TEACHMODEL_ISNULL);
//        }else {
//            update.setTeachmode(courseBase.getTeachmode());
//        }
//        if (StringUtils.isEmpty(courseBase.getUserId())){
//            ExceptionCast.cast(CourseCode.COURSE_USERID_ISNULL);
//        }else {
//            update.setUserId(courseBase.getUserId());
//        }
        if (StringUtils.isEmpty(courseBase.getUsers())){
            ExceptionCast.cast(CourseCode.COURSE_USERS_ISNULL);
        }else {
            update.setUsers(courseBase.getUsers());
        }
        CourseBase base = this.courseBaseRepository.saveAndFlush(update);
        return new UpdateCourseResult(base, CommonCode.SUCCESS);
    }

    /**
     * 根据课程id查询课程营销信息
     * @param id 课程id
     * @return
     */
    @Override
    public CourseMarket getCourseMarketById(String id) {
        if (id == null){
            ExceptionCast.cast(CourseCode.COURSE_ID_ISNULL);
        }
        Optional<CourseMarket> optional = this.courseMarketRepository.findById(id);
        if (!optional.isPresent()){
            ExceptionCast.cast(CourseCode.COURSE_MARKET_INFO_ISNULL);
        }
        return optional.get();
    }

    /**
     * 根据课程id插入或者更新营销信息
     * @param id 课程id
     * @param courseMarket 营销信息
     * @return
     */
    @Override
    public CourseMarketResult updateOrInsertCourseMarket(String id, CourseMarket courseMarket) {
        Optional<CourseMarket> optional = this.courseMarketRepository.findById(id);
        if (optional.isPresent()){
            CourseMarket temp = optional.get();
            //1.执行更新
            if (StringUtils.isEmpty(courseMarket.getCharge())){
                ExceptionCast.cast(CourseCode.COURSE_MARKET_INFO_CHARGE_ISNULL);
            }else {
                temp.setCharge(courseMarket.getCharge());
            }
            if (StringUtils.isEmpty(courseMarket.getId())){
                ExceptionCast.cast(CourseCode.COURSE_ID_ISNULL);
            }else {
                temp.setId(courseMarket.getId());
            }
            if (StringUtils.isEmpty(courseMarket.getQq())){
                ExceptionCast.cast(CourseCode.COURSE_MARKET_INFO_QQ_ISNULL);
            }else {
                temp.setQq(courseMarket.getQq());
            }
            if (StringUtils.isEmpty(courseMarket.getValid())){
                ExceptionCast.cast(CourseCode.COURSE_MARKET_INFO_VALID_ISNULL);
            }else {
                temp.setValid(courseMarket.getValid());
            }
            temp.setPrice(courseMarket.getPrice());
            temp.setPrice_old(courseMarket.getPrice_old());
            temp.setStartTime(courseMarket.getStartTime());
            temp.setEndTime(courseMarket.getEndTime());
            CourseMarket save = this.courseMarketRepository.saveAndFlush(temp);
            return new CourseMarketResult(save, CommonCode.SUCCESS);
        }else {
            //2.执行插入
            CourseMarket save = this.courseMarketRepository.save(courseMarket);
            return new CourseMarketResult(save, CommonCode.SUCCESS);
        }
    }

    /**
     * 保存课程图片
     * @param courseId 课程id
     * @param pic 图片地址
     * @return
     */
    @Override
    public ResponseResult saveCoursePic(String courseId, String pic) {
        //1.查询课程图片
        Optional<CoursePic> optional = this.coursePicRepository.findById(courseId);
        CoursePic coursePic = null;
        if (optional.isPresent()){
            coursePic = optional.get();
        }
        //2.没有课程图片则新建对象
        if (coursePic == null){
            coursePic = new CoursePic();
        }
        coursePic.setCourseid(courseId);
        coursePic.setPic(pic);
        this.coursePicRepository.save(coursePic);
        return new ResponseResult(CommonCode.SUCCESS);
    }

    /**
     * 查询课程图片
     * @param courseId 课程id
     * @return
     */
    @Override
    public CoursePic findCoursePic(String courseId) {
        Optional<CoursePic> optional = this.coursePicRepository.findById(courseId);
        if (!optional.isPresent()){
            ExceptionCast.cast(CourseCode.COURSE_PICISNULL);
        }
        return optional.get();
    }

    /**
     * 删除课程图片
     * @param courseId 课程id
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult deleteCoursePic(String courseId) {
        long result = this.coursePicRepository.deleteByCourseid(courseId);
        if (result > 0){
            return new ResponseResult(CommonCode.SUCCESS);
        }else {
            return new ResponseResult(CommonCode.FAIL);
        }
    }

    /**
     * 查询课程详情
     * @param id 课程id
     * @return
     */
    @Override
    public CourseView getCourseView(String id) {
        CourseView courseView = new CourseView();
        final CountDownLatch countDownLatch = new CountDownLatch(4);
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        try {
            CourseBase courseBaseResult = executorService.submit(() -> {
                //1.查询课程基础信息
                countDownLatch.countDown();
                Optional<CourseBase> courseBase = this.courseBaseRepository.findById(id);
                return courseBase.orElse(null);
            }).get();
            CourseMarket courseMarketResult = executorService.submit(() -> {
                //2.查询课程营销信息
                countDownLatch.countDown();
                Optional<CourseMarket> courseMarket = this.courseMarketRepository.findById(id);
                return courseMarket.orElse(null);
            }).get();
            CoursePic coursePicResult = executorService.submit(() -> {
                //3.查询课程图片
                countDownLatch.countDown();
                Optional<CoursePic> coursePic = this.coursePicRepository.findById(id);
                return coursePic.orElse(null);
            }).get();
            TeachplanNode teachplanNodeResult = executorService.submit(() -> {
                //4.查询课程教学计划
                countDownLatch.countDown();
                return this.teachPlanMapper.selectList(id);
            }).get();
            countDownLatch.await();
            courseView.setCourseBase(courseBaseResult);
            courseView.setCourseMarket(courseMarketResult);
            courseView.setCoursePic(coursePicResult);
            courseView.setTeachplanNode(teachplanNodeResult);
            return courseView;
        }catch (Exception e){
            ExceptionCast.cast(CourseCode.COURSE_VIEW_QUERY_ERROR);
        }
        return courseView;
    }


    /**
     * 课程详情页面预览
     * @param courseId 课程id
     * @return
     */
    @Override
    public CoursePublicResult preview(String courseId) {

        //1.发布课程预览页面，构建cmsPage对象
        CmsPage cmsPage = constructCmsPage(courseId);
        //2.保存cms信息
        CmsPageResult save = this.cmsPageClient.save(cmsPage);
        if (!save.isSuccess()){
            return new CoursePublicResult(CommonCode.FAIL,null);
        }
        //3.页面id
        String pageId = save.getCmsPage().getPageId();
        //4.页面url
        String pageUrl = previewUrl + pageId;
        return new CoursePublicResult(CommonCode.SUCCESS,pageUrl);
    }

    /**
     * 课程详情页面发布
     * @param courseId 课程id
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CoursePublicResult publish(String courseId) {
        //1.发布课程详情页面
        CmsPostPageResult cmsPostPageResult = publishPage(courseId);
        if (!cmsPostPageResult.isSuccess()){
            ExceptionCast.cast(CourseCode.COURSE_PUBLISH_ERROR);
        }
        //2.更新课程状态
        CourseBase courseBase = saveCoursePubState(courseId);
        //3.课程索引
        //3.1 创建课程索引
        CoursePub coursePub = createCoursePub(courseId);
        if (coursePub == null){
            ExceptionCast.cast(CourseCode.COURSE_PUBLISH_CREATE_INDEX_ERROR);
        }
        //3.2 保存课程索引信息
        saveCoursePub(courseId, coursePub);
        //4.课程缓存
        //5.封装返回结果
        return new CoursePublicResult(CommonCode.SUCCESS,cmsPostPageResult.getPageUrl());
    }

    /**
     * 保存课程发布对象
     * @param id 课程id
     * @param coursePub 课程索引对象
     * @return
     */
    public CoursePub saveCoursePub(String id, CoursePub coursePub) {
        if (StringUtils.isEmpty(id)){
            ExceptionCast.cast(CourseCode.COURSE_PUBLISH_COURSEIDISNULL);
        }
        CoursePub coursePubNew = null;
        Optional<CoursePub> optional = this.coursePubRepository.findById(id);
        if (optional.isPresent()){
            coursePubNew = optional.get();
        }
        if (coursePubNew == null){
            coursePubNew = new CoursePub();
        }
        BeanUtils.copyProperties(coursePub, coursePubNew);
        //设置主键
        coursePub.setId(id);
        //更新时间戳为最新时间
        coursePub.setTimestamp(new Date());
        //发布时间
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String date = simpleDateFormat.format(new Date());
        coursePub.setPubTime(date);
        return this.coursePubRepository.save(coursePub);
    }

    /**
     * 创建课程索引对象
     * @param id
     * @return
     */
    private CoursePub createCoursePub(String id){
        CourseView courseView = this.getCourseView(id);

        CoursePub coursePub = new CoursePub();
        coursePub.setId(id);
        //1.设置基础信息
        BeanUtils.copyProperties(courseView.getCourseBase(), coursePub);
        //2.设置课程图片
        BeanUtils.copyProperties(courseView.getCoursePic(), coursePub);
        //3.设置课程营销信息
        BeanUtils.copyProperties(courseView.getCourseMarket(), coursePub);
        //4.设置课程计划
        TeachplanNode teachplanNode = courseView.getTeachplanNode();
        String teachplanString = JSON.toJSONString(teachplanNode);
        coursePub.setTeachplan(teachplanString);
        return coursePub;

    }

    private CourseBase saveCoursePubState(String courseId) {
        CourseBase courseBase = this.getCourseById(courseId);
        //更新发布状态
        courseBase.setStatus("202002");
        return this.courseBaseRepository.save(courseBase);
    }

    private CmsPostPageResult publishPage(String courseId) {
        //构造cms_page对象
        CmsPage cmsPage = constructCmsPage(courseId);
        return cmsPageClient.postPageQuick(cmsPage);
    }

    private CmsPage constructCmsPage(String courseId) {
        CourseBase courseBase = this.getCourseById(courseId);

        CmsPage cmsPage = new CmsPage();
        cmsPage.setSiteId(publish_siteId);
        cmsPage.setTemplateId(publish_templateId);
        cmsPage.setPageWebPath(publish_page_webPath);
        cmsPage.setDataUrl(publish_dataUrlPre + courseId);
        cmsPage.setPagePhysicalPath(publish_page_physicalPath);
        cmsPage.setPageName(courseId+".html");
        cmsPage.setPageAliase(courseBase.getName());

        return cmsPage;
    }

    /**
     * 根据课程id获取一级节点
     * 如果没有找到，说明是第一次添加课程计划，需要构造根节点
     * @param courseId
     * @return
     */
    private String getTeachPlanRoot(String courseId) {
        //1.校验课程
        Optional<CourseBase> optional = this.courseBaseRepository.findById(courseId);
        if (!optional.isPresent()){
            ExceptionCast.cast(CourseCode.COURSE_PLAN_ADD_COURSEISNULL);
        }
        CourseBase courseBase = optional.get();
        //2.获取课程计划根节点
        List<Teachplan> teachplanList = this.teachPlanRepository.findByCourseidAndParentid(courseId, "0");
        if (teachplanList == null || teachplanList.size() == 0){
            //3.新增根节点
            Teachplan teachplanRoot = new Teachplan();
            teachplanRoot.setCourseid(courseId);
            teachplanRoot.setPname(courseBase.getName());
            teachplanRoot.setParentid("0");
            //层级：一级
            teachplanRoot.setGrade("1");
            //未发布
            teachplanRoot.setStatus("0");
            Teachplan save = teachPlanRepository.save(teachplanRoot);
            return save.getId();
        }
        Teachplan teachplan = teachplanList.get(0);
        return teachplan.getId();
    }
}
