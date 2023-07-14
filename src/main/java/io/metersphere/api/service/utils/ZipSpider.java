package io.metersphere.api.service.utils;

import io.metersphere.dto.ProjectJarConfig;
import io.metersphere.utils.JsonUtils;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Consts;
import org.springframework.http.HttpMethod;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipSpider {

    /**
     * 覆盖java默认的证书验证
     */
    private static final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[]{};
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }
    }};

    /**
     * 设置不验证主机
     */
    private static final HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

    /**
     * 信任所有
     */
    private static SSLSocketFactory trustAllHosts(HttpsURLConnection connection) {
        SSLSocketFactory oldFactory = connection.getSSLSocketFactory();
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLSocketFactory newFactory = sc.getSocketFactory();
            connection.setSSLSocketFactory(newFactory);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return oldFactory;
    }

    //解压本地文件至目的文件路径
    public static void unzip(String fromFile, String toFile) {
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(fromFile)); BufferedInputStream bin = new BufferedInputStream(zin);) {
            String parent = toFile;
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null && !entry.isDirectory()) {
                File fout = new File(parent, entry.getName());
                if (!fout.exists()) {
                    (new File(fout.getParent())).mkdirs();
                }
                try (FileOutputStream out = new FileOutputStream(fout);
                     BufferedOutputStream bout = new BufferedOutputStream(out);) {
                    int b;
                    while ((b = bin.read()) != -1) {
                        bout.write(b);
                    }
                    LoggerUtil.info(fout + "解压成功");
                } catch (Exception e) {
                    LoggerUtil.error(e);
                }
            }
        } catch (FileNotFoundException e) {
            LoggerUtil.error(e);
        } catch (IOException e) {
            LoggerUtil.error(e);
        }
    }

    @SuppressWarnings("finally")
    public static File downloadFile(String urlPath, String downloadDir) {
        OutputStream out = null;
        BufferedInputStream bin = null;
        HttpURLConnection httpURLConnection = null;
        try {
            URL url = new URL(urlPath);
            URLConnection urlConnection = url.openConnection();
            boolean useHttps = urlPath.startsWith("https");
            if (useHttps) {
                LoggerUtil.info("进入HTTPS协议处理方法");
                HttpsURLConnection https = (HttpsURLConnection) urlConnection;
                trustAllHosts(https);
                https.setHostnameVerifier(DO_NOT_VERIFY);
            }
            httpURLConnection = (HttpURLConnection) urlConnection;// http的连接类
            httpURLConnection.setConnectTimeout(1000 * 5);//设置超时
            httpURLConnection.setRequestMethod("GET");//设置请求方式，默认是GET
            httpURLConnection.setRequestProperty("Charset", "UTF-8");// 设置字符编码
            httpURLConnection.connect();// 打开连接

            bin = new BufferedInputStream(httpURLConnection.getInputStream());
            String fileName = httpURLConnection.getHeaderField("Content-Disposition");
            if (StringUtils.isEmpty(fileName)) {
                LoggerUtil.info(urlPath + " 下载的文件名称为空", fileName);
                fileName = "";
            } else {
                fileName = URLDecoder.decode(fileName.substring(fileName.indexOf("filename") + 10, fileName.length() - 1), "UTF-8");
            }
            String path = StringUtils.join(downloadDir, File.separatorChar, fileName);// 指定存放位置
            File file = new File(path);
            // 校验文件夹目录是否存在，不存在就创建一个目录
            if (file != null && file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            out = new FileOutputStream(file);
            int size = 0;

            byte[] b = new byte[2048];
            //把输入流的文件读取到字节数据b中，然后输出到指定目录的文件
            while ((size = bin.read(b)) != -1) {
                out.write(b, 0, size);
            }
            // 关闭资源
            bin.close();
            out.close();
            LoggerUtil.info("文件下载成功！");
            return file;
        } catch (MalformedURLException e) {
            LoggerUtil.error(e);
        } catch (IOException e) {
            LoggerUtil.error(e);
            LoggerUtil.info("文件下载失败！");
        } finally {
            try {
                if (bin != null) {
                    bin.close();
                }
                if (out != null) {
                    out.close();
                }
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
            } catch (Exception e) {
                LoggerUtil.error(e);
            }
        }
        return null;
    }

    public static File downloadJarHistory(String urlPath, String pluginId, String downloadDir) {
        byte[] t = JsonUtils.toJSONString(new ArrayList<>() {{
            this.add(pluginId);
        }}).getBytes(Consts.UTF_8);
        File file = getFile(urlPath, downloadDir, t);
        if (file != null) return file;
        return null;
    }

    public static File downloadJarDb(String urlPath, Map<String, List<ProjectJarConfig>> request, String downloadDir) {
        byte[] t = JsonUtils.toJSONString(request).getBytes(Consts.UTF_8);
        File file = getFile(urlPath, downloadDir, t);
        if (file != null) return file;
        return null;
    }

    private static File getFile(String urlPath, String downloadDir, byte[] t) {
        OutputStream out = null;
        BufferedInputStream bin = null;
        HttpURLConnection httpURLConnection = null;
        try {
            URL url = new URL(urlPath);
            URLConnection urlConnection = url.openConnection();
            boolean useHttps = urlPath.startsWith("https");
            if (useHttps) {
                LoggerUtil.info("进入HTTPS协议处理方法");
                HttpsURLConnection https = (HttpsURLConnection) urlConnection;
                trustAllHosts(https);
                https.setHostnameVerifier(DO_NOT_VERIFY);
            }
            httpURLConnection = (HttpURLConnection) urlConnection;
            httpURLConnection.setRequestMethod(HttpMethod.POST.name());
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setDoInput(true);
            httpURLConnection.setUseCaches(false);//POST请求不能使用缓存（POST不能被缓存）
            httpURLConnection.setRequestProperty("Connection", "Keep-Alive");
            httpURLConnection.setInstanceFollowRedirects(true);//设置只作用于当前的实例
            httpURLConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            httpURLConnection.setConnectTimeout(20 * 1000);//设置连接主机超时（单位：毫秒）
            httpURLConnection.setReadTimeout(20 * 1000);//设置从主机读取数据超时（单位：毫秒）
            httpURLConnection.connect();

            OutputStream outputStream = httpURLConnection.getOutputStream();
            outputStream.write(t);
            outputStream.flush();
            outputStream.close();

            bin = new BufferedInputStream(httpURLConnection.getInputStream());
            String fileName = httpURLConnection.getHeaderField("Content-Disposition");
            if (StringUtils.isEmpty(fileName)) {
                LoggerUtil.info(urlPath + " 下载的文件名称为空", fileName);
                fileName = "";
            } else {
                fileName = URLDecoder.decode(fileName.substring(fileName.indexOf("filename") + 10, fileName.length() - 1), "UTF-8");
            }
            String path = StringUtils.join(downloadDir, File.separatorChar, fileName);// 指定存放位置
            File file = new File(path);
            // 校验文件夹目录是否存在，不存在就创建一个目录
            if (file != null && file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            out = new FileOutputStream(file);
            int size = 0;

            byte[] b = new byte[2048];
            //把输入流的文件读取到字节数据b中，然后输出到指定目录的文件
            while ((size = bin.read(b)) != -1) {
                out.write(b, 0, size);
            }
            // 关闭资源
            bin.close();
            out.close();
            LoggerUtil.info("文件下载成功！");
            return file;
        } catch (MalformedURLException e) {
            LoggerUtil.error(e);
        } catch (IOException e) {
            LoggerUtil.error(e);
            LoggerUtil.info("文件下载失败！");
        } finally {
            try {
                if (bin != null) {
                    bin.close();
                }
                if (out != null) {
                    out.close();
                }
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
            } catch (Exception e) {
                LoggerUtil.error(e);
            }
        }
        return null;
    }

    public static File downloadFile(String urlPath, BodyFileRequest request, String downloadDir) {
        File file = null;
        try {
            URL url = new URL(urlPath);//创建连接
            URLConnection urlConnection = url.openConnection();
            boolean useHttps = urlPath.startsWith("https");
            if (useHttps) {
                LoggerUtil.info("进入HTTPS协议处理方法");
                HttpsURLConnection https = (HttpsURLConnection) urlConnection;
                trustAllHosts(https);
                https.setHostnameVerifier(DO_NOT_VERIFY);
            }
            HttpURLConnection connection = (HttpURLConnection) urlConnection;
            connection.setRequestMethod(HttpMethod.POST.name());
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);//POST请求不能使用缓存（POST不能被缓存）
            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setInstanceFollowRedirects(true);//设置只作用于当前的实例
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setConnectTimeout(20 * 1000);//设置连接主机超时（单位：毫秒）
            connection.setReadTimeout(20 * 1000);//设置从主机读取数据超时（单位：毫秒）
            connection.connect();

            OutputStream outputStream = connection.getOutputStream();
            byte[] t = JsonUtils.toJSONString(request).getBytes(Consts.UTF_8);
            outputStream.write(t);
            outputStream.flush();
            outputStream.close();
            // 写文件
            String fileName = connection.getHeaderField("Content-Disposition");
            fileName = URLDecoder.decode(fileName.substring(fileName.indexOf("filename") + 10, fileName.length() - 1), Consts.UTF_8);
            String path = downloadDir + File.separatorChar + fileName;// 指定存放位置
            file = new File(path);
            OutputStream out = new FileOutputStream(file);
            BufferedInputStream bin = new BufferedInputStream(connection.getInputStream());
            int size = 0;
            byte[] b = new byte[2048];
            //把输入流的文件读取到字节数据b中，然后输出到指定目录的文件
            while ((size = bin.read(b)) != -1) {
                out.write(b, 0, size);
            }
            out.close();
            bin.close();
        } catch (Exception e) {
            LoggerUtil.error(e);
        }
        return file;
    }
}
