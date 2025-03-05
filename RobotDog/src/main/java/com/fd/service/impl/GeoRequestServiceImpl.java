package com.fd.service.impl;

import com.fd.domain.GeoRequest;
import com.fd.service.GeoRequestService;
import com.fd.util.RedisCache;
import com.fd.util.ResponseResult;
import it.geosolutions.geoserver.rest.GeoServerRESTManager;
import it.geosolutions.geoserver.rest.GeoServerRESTPublisher;
import it.geosolutions.geoserver.rest.GeoServerRESTReader;
import it.geosolutions.geoserver.rest.decoder.RESTDataStore;
import it.geosolutions.geoserver.rest.decoder.RESTLayerGroupList;
import it.geosolutions.geoserver.rest.encoder.datastore.GSGeoTIFFDatastoreEncoder;
import it.geosolutions.geoserver.rest.manager.GeoServerRESTStoreManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
//TODO 修改注册服务中的上传
public class GeoRequestServiceImpl implements GeoRequestService {

    @Autowired
    private RedisCache redisCache;

    private String url = "http://127.0.0.1:8080/geoserver";    //geoserver的地址
    private String un = "admin";         //geoserver的账号
    private String pw = "geoserver";     //geoserver的密码



    @Override
    public ResponseResult upload(GeoRequest geoRequest, MultipartFile file) {
            try {

                //0.判断文件类型并判断是否为空
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
                Thread.sleep(2000);

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
                Thread.sleep(2000);


                //  4、发布图层服务
                    // 将 MultipartFile 转换为临时文件
                File tempFile = File.createTempFile("geotiff-", ".tif");
                file.transferTo(tempFile);
                    //redis
                redisCache.deleteObject(workspace+"layername");
                boolean b = publisher.publishGeoTIFF(workspace , storename , layerName , tempFile);
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

    @Override
    public ResponseResult getWmsInfo(String workspace, String layername) {
        Map<String, String> wmsInfo = new HashMap<>();
        wmsInfo.put("wmsUrl", "http://127.0.0.1:8080/geoserver/"+workspace+"/wms");
        wmsInfo.put("layerName", workspace+":"+layername);
        wmsInfo.put("srs", "EPSG:4326");
        wmsInfo.put("bbox", "minx,miny,maxx,maxy"); // 默认地图范围
        wmsInfo.put("format", "image/png");
        return new ResponseResult<>(200,"获取成功",wmsInfo);
    }
}
