package com.fd.controller;

import com.fd.domain.GeoRequest;
import com.fd.service.GeoRequestService;
import com.fd.util.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/geoserver")
public class GeoRequestController {


    @Autowired
    private GeoRequestService geoRequestService;



    @GetMapping("/upload")
    public ResponseResult upload(@RequestParam String workspace,@RequestParam MultipartFile file,@RequestParam String storename,@RequestParam String layerName){
        GeoRequest geoRequest=new GeoRequest(workspace,storename,layerName);
        return geoRequestService.upload(geoRequest,file);
    }

    @GetMapping("/getWorkplace")
    public ResponseResult getW(){
        return geoRequestService.get();
    }

    @GetMapping("/getLayername")
    public ResponseResult getL(@RequestParam String workspace){return geoRequestService.getL(workspace);}


    @GetMapping("/wms")
    public ResponseResult getWmsInfo(@RequestParam String workspace,@RequestParam String layername){
        return geoRequestService.getWmsInfo(workspace,layername);
    }





}
