package com.example.quanlybaotri.ai.config;

import java.time.Duration;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.ai")
@Validated
public class AiProperties {
    @NotBlank private String apiKey;
    @NotBlank private String apiBaseUrl = "https://openrouter.ai/api/v1";
    @NotBlank private String model = "nvidia/nemotron-3.5-super-vl";
    @Pattern(regexp="none|low|high") private String reasoningEffort = "none";
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(60);
    private Duration sessionTtl = Duration.ofMinutes(30);
    private int maxTurns = 20;
    private int maxInputLength = 2000;
    private int maxResults = 5;
    private int ratePerMinute = 10;
    @Min(1) private int classifyMaxOutputTokens = 256;
    @Min(1) private int maxOutputTokens = 400;
    @Min(1) private int generalMaxOutputTokens = 800;
    public String getApiKey(){return apiKey;} public void setApiKey(String v){apiKey=v;}
    public String getApiBaseUrl(){return apiBaseUrl;} public void setApiBaseUrl(String v){apiBaseUrl=v;}
    public String getModel(){return model;} public void setModel(String v){model=v;}
    public String getReasoningEffort(){return reasoningEffort;} public void setReasoningEffort(String v){reasoningEffort=v;}
    public Duration getConnectTimeout(){return connectTimeout;} public void setConnectTimeout(Duration v){connectTimeout=v;}
    public Duration getReadTimeout(){return readTimeout;} public void setReadTimeout(Duration v){readTimeout=v;}
    public Duration getSessionTtl(){return sessionTtl;} public void setSessionTtl(Duration v){sessionTtl=v;}
    public int getMaxTurns(){return maxTurns;} public void setMaxTurns(int v){maxTurns=v;}
    public int getMaxInputLength(){return maxInputLength;} public void setMaxInputLength(int v){maxInputLength=v;}
    public int getMaxResults(){return maxResults;} public void setMaxResults(int v){maxResults=v;}
    public int getRatePerMinute(){return ratePerMinute;} public void setRatePerMinute(int v){ratePerMinute=v;}
    public int getClassifyMaxOutputTokens(){return classifyMaxOutputTokens;} public void setClassifyMaxOutputTokens(int v){classifyMaxOutputTokens=v;}
    public int getMaxOutputTokens(){return maxOutputTokens;} public void setMaxOutputTokens(int v){maxOutputTokens=v;}
    public int getGeneralMaxOutputTokens(){return generalMaxOutputTokens;} public void setGeneralMaxOutputTokens(int v){generalMaxOutputTokens=v;}
}
