package io.metersphere.api.jmeter.utils;

import org.apache.commons.lang3.StringUtils;
import java.net.MalformedURLException;
import java.net.URL;

public class URLParserUtil {
    private static final String QUOTES = "://";

    public static String getHost(String platformURL) throws MalformedURLException {
        URL urlObject = new URL(platformURL);
        return StringUtils.join(urlObject.getProtocol(), QUOTES, urlObject.getHost(), (urlObject.getPort() > 0 ? ":" + urlObject.getPort() : StringUtils.EMPTY));
    }

    public static String getPluginURL(String platformURL) throws MalformedURLException {
        return StringUtils.join(getHost(platformURL), "/api/jmeter/download/plug/jar");
    }

    public static String getJarURL(String platformURL) throws MalformedURLException {
        return StringUtils.join(getHost(platformURL), "/api/jmeter/download/jar");
    }

    public static String getDownFileURL(String platformURL) throws MalformedURLException {
        return StringUtils.join(getHost(platformURL), "/api/jmeter/download/files");
    }

    public static String getScriptURL(String platformURL) throws MalformedURLException {
        return StringUtils.join(getHost(platformURL), "/api/jmeter/get-script");
    }
}
