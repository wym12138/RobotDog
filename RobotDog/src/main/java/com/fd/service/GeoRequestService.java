package com.fd.service;

import com.fd.domain.GeoRequest;
import com.fd.util.ResponseResult;
import org.springframework.web.multipart.MultipartFile;

public interface GeoRequestService {
    ResponseResult upload(GeoRequest geoRequest, String file);

    ResponseResult get();

    ResponseResult getL(String workspace);



    ResponseResult postFile(MultipartFile file);

    ResponseResult getXY(String name);

}
