package com.ghondur.share;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ApiClient {
    private ApiClient() {}

    public static String getText(String u) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setRequestProperty("User-Agent","GhondurShare/1.0");
        int code=c.getResponseCode(); InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
        byte[] b=readAll(in); c.disconnect();
        String s=new String(b, StandardCharsets.UTF_8);
        if(code<200||code>=300) throw new IllegalStateException("HTTP "+code+": "+s);
        return s;
    }

    public static DownloadedFile getBytes(String u) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setInstanceFollowRedirects(true); c.setRequestProperty("User-Agent","GhondurShare/1.0");
        int code=c.getResponseCode(); if(code<200||code>=300) throw new IllegalStateException("HTTP "+code);
        String type=c.getContentType(); byte[] b=readAll(c.getInputStream()); c.disconnect(); return new DownloadedFile(b,type);
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[16384]; int n;
        while((n=in.read(buf))!=-1) out.write(buf,0,n); in.close(); return out.toByteArray();
    }

    public static final class DownloadedFile {
        public final byte[] data; public final String contentType;
        DownloadedFile(byte[] d,String t){data=d;contentType=t;}
    }
}
