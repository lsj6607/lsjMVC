package com.lsj.servlet;

import com.lsj.annotation.MyController;
import com.lsj.annotation.MyRequestMapping;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class MyDispatcherServlet extends HttpServlet {
    private Properties properties = new Properties();
    private List<String> classNames = new ArrayList<>();
    private Map<String,Object> ioc = new HashMap<>();
    private Map<String,Method> handlerMapping = new HashMap<>();
    private Map<String,Object> controllerMapping = new HashMap<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1.加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //2.初始化所有相关联的类,扫描用户设定的包下面所有的类
        doScanner(properties.getProperty("scanPackage"));
        //3.拿到扫描到的类,通过反射机制,实例化,并且放到ioc容器中(k-v  beanName-bean) beanName默认是首字母小写
        doInstance();
        //4.初始化HandlerMapping(将url和method对应上)
        initHandlerMapping();
    }

    @Override
    protected void doGet(HttpServletRequest req,HttpServletResponse resp) throws IOException{
        this.doPost(req, resp);
    }

    @Override
    protected  void doPost(HttpServletRequest req,HttpServletResponse resp) throws IOException{
        try {
            doDipatch(req,resp);
        }catch (Exception e){
            resp.getWriter().write("500! Server Exception");
        }
    }

    private void initHandlerMapping(){
        if(ioc.isEmpty()){
            return;
        }

        try{
            for(Map.Entry<String,Object> entry:ioc.entrySet()){
                Class<? extends Object> clazz = entry.getValue().getClass();
                if(!clazz.isAnnotationPresent(MyController.class)){
                    continue;
                }
                //拼url时,是controller头的url拼上方法上的url
                String baseUrl = "";
                if(clazz.isAnnotationPresent(MyRequestMapping.class)){
                    MyRequestMapping annotation = clazz.getAnnotation(MyRequestMapping.class);
                    baseUrl = annotation.value();
                }
                Method[] methods = clazz.getMethods();
                for(Method method:methods){
                    if(!method.isAnnotationPresent(MyRequestMapping.class)){
                        continue;
                    }

                    MyRequestMapping annotation = method.getAnnotation(MyRequestMapping.class);
                    String url = annotation.value();

                    url = (baseUrl+"/"+url).replaceAll("/+","/");
                    handlerMapping.put(url,method);
                    controllerMapping.put(url,clazz.newInstance());
                    System.out.println(url+","+method);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void doInstance(){
        if(classNames.isEmpty()){
            return;
        }
        for(String className:classNames){
            try{
                Class<?> clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(MyController.class)){
                    ioc.put(toLowerFirstWord(clazz.getSimpleName()),clazz.newInstance());
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private void doLoadConfig(String location){
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(location);
        try{
            properties.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(null!=resourceAsStream){
                try{
                    resourceAsStream.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
    }

    private  void doScanner(String packageName){
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());
        for(File file:dir.listFiles()){
            if(file.isDirectory()){
                doScanner(packageName+"."+file.getName());
            }else{
                String className = packageName + "." +file.getName().replace(".class","");
                classNames.add(className);
            }
        }
    }

    /**
     * 把字符串的首字母小写
     * @param name
     * @return
     */
    private String toLowerFirstWord(String name){
        char[] charArray = name.toCharArray();
        charArray[0] += 32;
        return String.valueOf(charArray);
    }

    private void doDipatch(HttpServletRequest req,HttpServletResponse resp) throws IOException{
        if(handlerMapping.isEmpty()){
            return;
        }

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();

        url = url.replace(contextPath,"").replaceAll("/+","/");

        if(!handlerMapping.containsKey(url)){
            resp.getWriter().write("404 NOT FOUNT!");
        }

        Method method = this.handlerMapping.get(url);

        //获取方法的参数列表
        Class<?>[] parameterTypes = method.getParameterTypes();

        //获取请求的参数
        Map<String,String[]> parameterMap = req.getParameterMap();

        //保存参数值
        Object[] paramValues = new Object[parameterTypes.length];

        //方法的参数列表
        for(int i=0;i<parameterTypes.length;i++){
            String requestParam = parameterTypes[i].getSimpleName();

            if(requestParam.equals("HttpServletRequest")){
                paramValues[i] = req;
                continue;
            }

            if(requestParam.equals("HttpServletResponse")){
                paramValues[i] = resp;
                continue;
            }

            if(requestParam.equals("String")){
                for(Map.Entry<String,String[]> param:parameterMap.entrySet()){
                    String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]+","").replaceAll("\\s",",");
                    paramValues[i]=value;
                }
            }
        }

        //利用反射机制来调用
        try{
            method.invoke(this.controllerMapping.get(url),paramValues);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
