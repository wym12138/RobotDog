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
    public ResponseResult upload(@RequestParam(required = true) String workspace,@RequestParam(required = true) String file,@RequestParam(required = true) String storename,@RequestParam(required = true) String layerName){
        GeoRequest geoRequest=new GeoRequest(workspace,storename,layerName);
        return geoRequestService.upload(geoRequest,file);
    }

    @GetMapping("/getWorkplace")
    public ResponseResult getW(){
        return geoRequestService.get();
    }

    @GetMapping("/getLayername")
    public ResponseResult getL(@RequestParam(required = true) String workspace){return geoRequestService.getL(workspace);}



    @PostMapping("/postFile")
    public ResponseResult postFile(@RequestParam MultipartFile file){
        return geoRequestService.postFile(file);
    }


    @GetMapping("/getXY")
    public ResponseResult getXY(@RequestParam String name){
        return geoRequestService.getXY(name);
    }



}
