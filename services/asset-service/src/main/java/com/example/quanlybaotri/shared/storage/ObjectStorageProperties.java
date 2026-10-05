package com.example.quanlybaotri.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.storage")
public class ObjectStorageProperties {
    private String endpoint = "";
    private String publicEndpoint = "";
    private String region = "us-east-1";
    private boolean pathStyle;
    private String bucket;
    private String accessKey = "";
    private String secretKey = "";
    private int previewMinutes = 2;
    public String getEndpoint(){return endpoint;} public void setEndpoint(String v){endpoint=v;}
    public String getPublicEndpoint(){return publicEndpoint;} public void setPublicEndpoint(String v){publicEndpoint=v;}
    public String getRegion(){return region;} public void setRegion(String v){region=v;}
    public boolean isPathStyle(){return pathStyle;} public void setPathStyle(boolean v){pathStyle=v;}
    public String getBucket(){return bucket;} public void setBucket(String v){bucket=v;}
    public String getAccessKey(){return accessKey;} public void setAccessKey(String v){accessKey=v;}
    public String getSecretKey(){return secretKey;} public void setSecretKey(String v){secretKey=v;}
    public int getPreviewMinutes(){return previewMinutes;} public void setPreviewMinutes(int v){previewMinutes=v;}
}
