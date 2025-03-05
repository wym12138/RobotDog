package com.fd.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeoRequest {

    private String workspace;
    private String storename;
    private String layerName;

}
