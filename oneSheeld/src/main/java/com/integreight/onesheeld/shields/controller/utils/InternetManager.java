package com.integreight.onesheeld.shields.controller.utils;

import android.content.Context;
import android.util.Pair;

import com.integreight.onesheeld.model.InternetRequest;
import com.integreight.onesheeld.utils.ConnectionDetector;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.snappydb.DB;
import com.snappydb.DBFactory;
import com.snappydb.SnappydbException;

import org.apache.http.Header;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Hashtable;

/**
 * Created by Saad on 1/26/15.
 */
public class InternetManager {
    private static InternetManager ourInstance;
    private AsyncHttpClient httpClient;
    private Hashtable<Integer, InternetRequest> requests;
    private DB cachDB;
    private Context context;
    private AsyncHttpResponseHandler uiCallback;
    private String contentType = "";
    private int maxSentBytes = 64;
    private Pair<String, String> basicAuth;


    private InternetManager() {
        httpClient = new AsyncHttpClient();
        requests = new Hashtable<>();
        basicAuth = null;
    }

    public static synchronized InternetManager getInstance() {
        if (ourInstance == null) {
            ourInstance = new InternetManager();
        }
        return ourInstance;
    }

    public static InternetManager resetInstance() {
        try {
            getInstance().close();
        } catch (Exception e) {
        }
        ourInstance = new InternetManager();
        return ourInstance;
    }

    public void init(Context context) throws SnappydbException {
        this.context = context;
        cachDB = DBFactory.open(context);
    }

    public void close() throws SnappydbException {
        if (httpClient != null)
            httpClient.cancelRequests(context, true);
        if (requests != null)
            requests.clear();
        if (cachDB != null && cachDB.isOpen()) {
            cachDB.close();
            cachDB.destroy();
        }
        context = null;
    }

    public void cancelAllRequests() {
        if (httpClient != null)
            httpClient.cancelRequests(context, true);
    }

    public AsyncHttpClient getHttpClient() {
        if (httpClient == null)
            httpClient = new AsyncHttpClient();
        return httpClient;
    }

    public Hashtable<Integer, InternetRequest> getRequests() {
        return requests;
    }

    public synchronized InternetRequest getRequest(int id) {
        InternetRequest request = requests.get(id);
        if (request != null)
            return request;
        else {
            return null;
        }
    }

    public synchronized void putRequest(int id, final InternetRequest request) {
//        request.setContentType(contentType);
        requests.put(id, request);
        if (uiCallback != null)
            uiCallback.onStart();
    }

    public AsyncHttpResponseHandler getUiCallback() {
        return uiCallback;
    }

    public void setUiCallback(AsyncHttpResponseHandler uiCallback) {
        this.uiCallback = uiCallback;
    }

    public EXECUTION_TYPE execute(int id, REQUEST_TYPE type) {
        if (!ConnectionDetector.isConnectingToInternet(context))
            return EXECUTION_TYPE.NO_INTERNET;
        final InternetRequest request = requests.get(id);
        if (request == null)
            return EXECUTION_TYPE.REQUEST_NOT_FOUND;
        if (request.getStatus() == InternetRequest.REQUEST_STATUS.SENT || request.getStatus() == InternetRequest.REQUEST_STATUS.CALLED)
            return EXECUTION_TYPE.ALREADY_EXECUTING;
        if (request.getUrl() == null || request.getUrl().trim().length() == 0)
            return EXECUTION_TYPE.NO_URL;
//        if (request.getRegisteredCallbacks() == null || request.getRegisteredCallbacks().size() == 0)
//            return EXECUTION_TYPE.NO_CALLBACKS;
        final AsyncHttpResponseHandler withUiCallBack = new AsyncHttpResponseHandler() {
            @Override
            public void onStart() {
                if (request.getCallback() != null)
                    request.getCallback().onStart();
                if (getUiCallback() != null)
                    getUiCallback().onStart();
                super.onStart();
            }

            @Override
            public void onFinish() {
                if (request.getCallback() != null)
                    request.getCallback().onFinish();
                if (getUiCallback() != null)
                    getUiCallback().onFinish();
                super.onFinish();
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                if (request.getCallback() != null)
                    request.getCallback().onFailure(statusCode, headers, responseBody, error);
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                if (request.getCallback() != null)
                    request.getCallback().onSuccess(statusCode, headers, responseBody);
            }

            @Override
            public void onProgress(int bytesWritten, int totalSize) {
                if (request.getCallback() != null)
                    request.getCallback().onProgress(bytesWritten, totalSize);
                super.onProgress(bytesWritten, totalSize);
            }

        };
        if (InternetManager.getInstance().getBasicAuth() != null && InternetManager.getInstance().getBasicAuth().first != null && InternetManager.getInstance().getBasicAuth().first.trim().length() > 0)
            getHttpClient().setBasicAuth(InternetManager.getInstance().getBasicAuth().first, InternetManager.getInstance().getBasicAuth().second);

        switch (type) {
            case GET:
                getHttpClient().get(context, request.getUrl(), request.getHeaders(), request.getParams(), withUiCallBack);
                break;
            case POST:
                getHttpClient().post(context, request.getUrl(), request.getHeaders(), request.getParams(), request.getContentType(), withUiCallBack);
                break;
            case PUT:
                getHttpClient().put(context, request.getUrl(), request.getParams(), withUiCallBack);
                break;
            case DELETE:
                getHttpClient().delete(context, request.getUrl(), request.getHeaders(), request.getParams(), withUiCallBack);
                break;
        }
        getRequest(id).setStatus(InternetRequest.REQUEST_STATUS.CALLED);
        return EXECUTION_TYPE.SUCCESSFUL;
    }

    public DB getCachDB() {
        return cachDB;
    }

    public void disponseResponse(int id) {
//        if (requests.contains(id)) {
//            requests.get(id).ignoreResponse();
//            requests.remove(id);
//        }
        try {
            cachDB.del(id + "");
        } catch (SnappydbException e) {
            e.printStackTrace();
        }
        if (uiCallback != null)
            uiCallback.onStart();
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public int getMaxSentBytes() {
        return maxSentBytes;
    }

    public void setMaxSentBytes(int maxSentBytes) {
        this.maxSentBytes = maxSentBytes > 255 ? 255 : maxSentBytes;
    }

    public Pair<String, String> getBasicAuth() {
        return basicAuth;
    }

    public void setBasicAuth(Pair<String, String> basicAuth) {
        try {
            this.basicAuth = new Pair<>(URLEncoder.encode(basicAuth.first, "UTF-8"), URLEncoder.encode(basicAuth.second, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            this.basicAuth = new Pair<>(basicAuth.first, basicAuth.second);
            e.printStackTrace();
        }
    }

    public void clearBasicAuth() {
        this.basicAuth = null;
    }

    public enum EXECUTION_TYPE {
        NO_INTERNET(0), SUCCESSFUL(-1), REQUEST_NOT_FOUND(1), ALREADY_EXECUTING(3), NO_URL(2);
        public int value = -1;

        private EXECUTION_TYPE(int value) {
            this.value = value;
        }
    }

    public enum REQUEST_TYPE {
        GET, POST, DELETE, PUT
    }

}