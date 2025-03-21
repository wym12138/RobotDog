package com.fd.service.impl;

import com.fd.domain.GeoRequest;
import com.fd.service.GeoRequestService;
import com.fd.util.RedisCache;
import com.fd.util.ResponseResult;
import it.geosolutions.geoserver.rest.GeoServerRESTManager;
import it.geosolutions.geoserver.rest.GeoServerRESTPublisher;
import it.geosolutions.geoserver.rest.GeoServerRESTReader;

import it.geosolutions.geoserver.rest.decoder.RESTCoverage;
import it.geosolutions.geoserver.rest.decoder.RESTDataStore;
import it.geosolutions.geoserver.rest.decoder.RESTLayer;
import it.geosolutions.geoserver.rest.decoder.RESTLayerGroupList;

import it.geosolutions.geoserver.rest.encoder.GSResourceEncoder;
import it.geosolutions.geoserver.rest.encoder.datastore.GSGeoTIFFDatastoreEncoder;
import it.geosolutions.geoserver.rest.manager.GeoServerRESTStoreManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
//TODO 修改注册服务中的上传
public class GeoRequestServiceImpl implements GeoRequestService {

    @Autowired
    private RedisCache redisCache;

    private String url = "http://202.140.140.215:9999/geoserver";    //geoserver的地址
    private String un = "admin";         //geoserver的账号
    private String pw = "geoserver";     //geoserver的密码



    @Override
    public ResponseResult upload(GeoRequest geoRequest, String file) {
            try {

                //0.判断文件类型并判断是否为空
//                String name = file.getOriginalFilename();
//                if (name == null) {
//                    return ResponseResult.okResult(400, "文件名不能为空");
//                }
//
//                String[] split = name.split("\\.");
//                if (split.length == 0) {
//                    return ResponseResult.okResult(400, "文件名格式错误");
//                }

//                String extension = split[split.length - 1];
//                if (!extension.equalsIgnoreCase("tif") && !extension.equalsIgnoreCase("tiff")) {
//                    return ResponseResult.okResult(400, "文件类型上传错误，仅支持 tif 或 tiff 格式");
//                }
                if (file==null){
                    return ResponseResult.okResult(400,"url不得为空");
                }
                if (geoRequest.getStorename()==null){
                    return ResponseResult.okResult(400,"数据源名称不得为空");
                }
                if (geoRequest.getLayerName()==null){
                    return ResponseResult.okResult(400,"图层名称不得为空");
                }
                if (geoRequest.getWorkspace()==null){
                    return ResponseResult.okResult(400,"工作区名称不得为空");
                }



                String workspace = geoRequest.getWorkspace();     //工作区名称
                String storename = geoRequest.getStorename();     //数据源名称（最后发布的服务title也是这个）
                String layerName = geoRequest.getLayerName();      //图层名称，任意起


                //  1、获取geoserver连接对象
                GeoServerRESTManager manager = null;

                try {
                    manager = new GeoServerRESTManager(new URL(url) , un , pw);
                }catch (Exception e){
                    e.printStackTrace();
                    System.out.println("geoserver服务器连接失败");
                    return ResponseResult.okResult(400,"geoserver服务器连接失败");

                }

                GeoServerRESTReader reader = manager.getReader();
                GeoServerRESTPublisher publisher = manager.getPublisher();
                GeoServerRESTStoreManager storeManager = manager.getStoreManager();

                //  2、判断是否有工作区，没有则创建
                boolean b2 = reader.existsWorkspace(workspace);
                if(!b2){
                    boolean b = publisher.createWorkspace(workspace);
                    //redis
                    redisCache.deleteObject("workspace");
                    if(!b){
                        System.out.println("工作区创建失败");
                        return ResponseResult.okResult(400,"工作区创建失败");
                    }
                }
                        // 等待 2 秒，确保工作区初始化完成
                Thread.sleep(5000);

                // 3. 创建数据源，有则报错
                RESTDataStore datastore = reader.getDatastore(workspace, storename);
                if (datastore == null) {
                    GSGeoTIFFDatastoreEncoder geoTIFFDatastore = new GSGeoTIFFDatastoreEncoder(storename);
                    geoTIFFDatastore.setWorkspaceName(workspace);
                    geoTIFFDatastore.setName(storename);
                    if (!storeManager.create(workspace, geoTIFFDatastore)) {
                        return ResponseResult.okResult(400, "创建数据源失败");
                    }
                } else {
                    System.out.println("数据源已存在，跳过创建");
                    return ResponseResult.okResult(400,"数据源名称重复");
                }
                        // 等待 2 秒，确保工作区初始化完成
                Thread.sleep(5000);


                //  4、发布图层服务
                // 3.5. 核心修改：配置坐标系参数
                    // 将 MultipartFile 转换为临时文件
                //File tempFile = File.createTempFile("geotiff-", ".tif");
                //file.transferTo(tempFile);
                File geoTiffFile = new File(file);
                if (!geoTiffFile.exists()) {
                    return ResponseResult.okResult(400, "文件不存在");
                }
                //redis
                redisCache.deleteObject(workspace+"layername");
                String srs = "EPSG:4326"; // 目标坐标参考系统（SRS）
                it.geosolutions.geoserver.rest.encoder.GSResourceEncoder.ProjectionPolicy policy =
                        GSResourceEncoder.ProjectionPolicy.REPROJECT_TO_DECLARED; // 设置投影策略
                String defaultStyle = "raster_style"; //设置为空
                double[] bbox = {-180.0, -90.0, 180.0, 90.0}; // 替换为你的边界框
                boolean b = publisher.publishGeoTIFF(workspace , storename , layerName , geoTiffFile,srs,policy,"",null);
                if(!b){
                    System.out.println("geotiff图层服务发布失败");
                    return ResponseResult.okResult(400,"geotiff图层服务发布失败");
                }



                return ResponseResult.okResult();


            }catch (Exception e){
                e.printStackTrace();
                return ResponseResult.okResult(400,"出现错误");
            }


    }

    @Override
    public ResponseResult get() {
        //判断redis是否有
        List<String> workspaceNames = redisCache.getCacheList("workspace");
        if (workspaceNames.size()!=0){
            return new ResponseResult<>(200,"获取成功",workspaceNames);
        }
        //连接geoserver服务
        GeoServerRESTManager manager = null;
        try {
            manager = new GeoServerRESTManager(new URL(url) , un , pw);
        }catch (Exception e){
            e.printStackTrace();
            System.out.println("geoserver服务器连接失败");
            return ResponseResult.okResult(400,"geoserver服务器连接失败");

        }
        //获取reader对象
        GeoServerRESTReader reader = manager.getReader();
        //获取工作区名称
        workspaceNames = reader.getWorkspaceNames();
        //存入redis
        redisCache.setCacheList("workspace",workspaceNames);

        return new ResponseResult<>(200,"获取成功",workspaceNames);
    }

    @Override
    public ResponseResult getL(String workspace) {
        if (workspace==null){
            return ResponseResult.okResult(400,"工作区不能为空");
        }
        //判断redis是否有
        List<String> names = redisCache.getCacheList(workspace+"layername");
        if (names!=null ||!names.isEmpty()||names.size()!=0){
            return new ResponseResult<>(200,"获取成功",names);
        }

        //连接geoserver服务
        GeoServerRESTManager manager = null;
        try {
            manager = new GeoServerRESTManager(new URL(url) , un , pw);
        }catch (Exception e){
            e.printStackTrace();
            System.out.println("geoserver服务器连接失败");
            return ResponseResult.okResult(400,"geoserver服务器连接失败");

        }

        //获取reader对象
        GeoServerRESTReader reader = manager.getReader();

        //获取图层名称
        try {
            RESTLayerGroupList layerGroups = reader.getLayerGroups(workspace);
            names=layerGroups.getNames();
        }catch (Exception e){
            return ResponseResult.okResult(400,"获取失败，workspace名字可能错误");
        }

        //存入redis
        redisCache.setCacheList(workspace+"layername",names);


        return new ResponseResult<>(200,"获取成功",names);
    }



    private final String uploadDir="/var/www/uploads/feidian/";
    private final String uploadDir2="D:\\feidianText";
    @Override
    public ResponseResult postFile(MultipartFile file) {
        //0.判断文件类型并判断是否为空//////
                if (file.isEmpty()){
                    return ResponseResult.okResult(400,"请上传文件");
                }
                String name = file.getOriginalFilename();
                if (name == null) {
                    return ResponseResult.okResult(400, "文件名不能为空");
                }

                String[] split = name.split("\\.");
                if (split.length == 0) {
                    return ResponseResult.okResult(400, "文件名格式错误");
                }

                String extension = split[split.length - 1];
                if (!extension.equalsIgnoreCase("tif") && !extension.equalsIgnoreCase("tiff")) {
                    return ResponseResult.okResult(400, "文件类型上传错误，仅支持 tif 或 tiff 格式");
                }
                name=UUID.randomUUID().toString()+name;


        Path filePath;
                try {
                    //1.存入服务器url中
                    //创建存储目录
                    File uploadPath = new File(uploadDir);
                    if (!uploadPath.exists()) {
                        uploadPath.mkdirs();
                    }

                    //保存文件
                    filePath = Paths.get(uploadDir, name);
                    Files.copy(file.getInputStream(), filePath);////////////////////////
                }catch (IOException e){
                    return ResponseResult.okResult(400,"文件上传失败");
                }

        String uri = uploadDir + name;

        return new ResponseResult(200,"储存成功",uri);
    }

    @Override
    public ResponseResult getXY(String name) {
        Map<String,Double> map=new HashMap<>();
        try {
            //  1、获取geoserver连接对象
            GeoServerRESTManager manager = null;

            try {
                manager = new GeoServerRESTManager(new URL(url) , un , pw);
            }catch (Exception e){
                e.printStackTrace();
                System.out.println("geoserver服务器连接失败");
                return ResponseResult.okResult(400,"geoserver服务器连接失败");

            }
            //1.5获取reader对象
            GeoServerRESTReader reader = manager.getReader();

            //2.获取图层及边界值
            RESTLayer layer = reader.getLayer(name, name);
            if (layer!=null){
                RESTCoverage coverage =reader.getCoverage(layer);
                if (coverage!=null){
                    // 获取图层的边界范围
                    map.put("minX",coverage.getMinX());
                    map.put("maxX",coverage.getMaxX());
                    map.put("minY",coverage.getMinY());
                    map.put("maxY",coverage.getMaxY());

                }
            }else {
                return new ResponseResult<>(400,"图层获取失败");
            }

            return ResponseResult.okResult(map);


        }catch (Exception e){
            return new ResponseResult<>(400,"出现错误");
        }
    }
}
