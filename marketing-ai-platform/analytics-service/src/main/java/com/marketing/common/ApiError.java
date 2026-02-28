package com.marketing.common;
import java.util.Map;
public record ApiError(String requestId,String error,String message,Map<String,Object> details){}
