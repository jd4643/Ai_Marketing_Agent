package com.marketing.common;
import jakarta.servlet.*;import jakarta.servlet.http.HttpServletRequest;import jakarta.servlet.http.HttpServletResponse;import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;import java.util.Collections;import java.util.Enumeration;import java.util.UUID;import org.slf4j.MDC;import org.springframework.stereotype.Component;
@Component
public class RequestIdFilter implements Filter {
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
    HttpServletRequest r=(HttpServletRequest)req; HttpServletResponse h=(HttpServletResponse)res;
    String originalId=r.getHeader("X-Request-Id"); boolean generated=(originalId==null||originalId.isBlank()); String id=originalId;
    if(generated) id=UUID.randomUUID().toString();
    MDC.put("requestId",id); h.setHeader("X-Request-Id",id); req.setAttribute("X-Request-Id",id);
    ServletRequest requestToUse=req; if(generated) requestToUse=new RequestIdRequestWrapper(r,id);
    try{chain.doFilter(requestToUse,res);} finally {MDC.remove("requestId");}
  }

  private static class RequestIdRequestWrapper extends HttpServletRequestWrapper {
    private final String requestId;
    RequestIdRequestWrapper(HttpServletRequest request,String requestId){super(request);this.requestId=requestId;}
    @Override public String getHeader(String name){ if("X-Request-Id".equalsIgnoreCase(name)) return requestId; return super.getHeader(name);}
    @Override public Enumeration<String> getHeaders(String name){ if("X-Request-Id".equalsIgnoreCase(name)) return Collections.enumeration(Collections.singletonList(requestId)); return super.getHeaders(name);}
    @Override public Enumeration<String> getHeaderNames(){ Enumeration<String> names=super.getHeaderNames(); java.util.List<String> list=Collections.list(names); boolean present=list.stream().anyMatch(h->"X-Request-Id".equalsIgnoreCase(h)); if(!present) list.add("X-Request-Id"); return Collections.enumeration(list);}
  }
}
