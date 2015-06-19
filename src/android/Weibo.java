package com.hiliaox;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import  android.media.ThumbnailUtils;

import com.sina.weibo.sdk.api.ImageObject;
import com.sina.weibo.sdk.api.TextObject;
import com.sina.weibo.sdk.api.VideoObject;
import com.sina.weibo.sdk.api.WebpageObject;
import com.sina.weibo.sdk.api.WeiboMultiMessage;
import com.sina.weibo.sdk.api.share.IWeiboShareAPI;
import com.sina.weibo.sdk.api.share.SendMultiMessageToWeiboRequest;
import com.sina.weibo.sdk.api.share.WeiboShareSDK;

import com.sina.weibo.sdk.auth.AuthInfo;
import com.sina.weibo.sdk.auth.Oauth2AccessToken;
import com.sina.weibo.sdk.auth.WeiboAuthListener;
import com.sina.weibo.sdk.auth.sso.SsoHandler;
import com.sina.weibo.sdk.exception.WeiboException;

import com.sina.weibo.sdk.utils.Utility;

public class Weibo extends CordovaPlugin {

    private static final int WEIBO_NOT_INSTALLED = 1;
    private static final int WEIBO_AUTHORIZE_CANCELED = 2;
    private static final int WEIBO_INVALID_TOKEN = 3;
    private static final int WEIBO_UNKNOWN_ERROR = 99;
    
    private static final String TAG = "Cordova-Weibo-SSO";

    private String appKey;

    private SsoHandler mSsoHandler = null;
    
    private IWeiboShareAPI mWeiboShareAPI = null;

    @Override
    public boolean execute(String action, final JSONArray args,
            final CallbackContext context) throws JSONException {
        boolean result = false;
        Activity activity = this.cordova.getActivity();
        try {
            if (action.equals("init")) {
                this.init(args, context);
                result = true;
            } else if (action.equals("login")) {
                this.login(context);
                result = true;
            } else if (action.equals("isInstalled")) {
                this.checkWeibo(context);
                result = true;
            } else if (action.equals("share")) {
                final Weibo me = this;
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        try {
                            
                            JSONObject cfg = args.getJSONObject(0);
                            if (cfg.getString("type").equals("text")) {
                                me.sendText(cfg.getString("text"),context);
                                context.success(1);
                            } else if (cfg.getString("type").equals("image")) {
                                me.sendImage(cfg.getString("data"),
                                        cfg.getString("text"),context);
                                context.success(1);
                            } else if (cfg.getString("type").equals("webpage")){
                                me.sendPage(cfg.getString("page_url"),
                                        cfg.getString("thumb_url"),
                                        cfg.getString("title"),
                                        cfg.getString("desc"),
                                        cfg.getString("text"),
                                        cfg.getString("image_url"));
                                context.success(1);
                            }
                        } catch (MalformedURLException e) {
                            context.error("JSON Exception:MalformedURLException:"+e.toString());
                            e.printStackTrace();
                        } catch (IOException e) {
                            context.error("JSON Exception:IOException:"+e.toString());
                            e.printStackTrace();
                        } catch (JSONException e) {
                            context.error("JSON Exception:JSONException:"+e.toString());
                            e.printStackTrace();
                        }                   
                    }
                });
                result = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            context.error(new ErrorMessage(10000,e.getMessage()));
            result = false;
        }
        return result;
    }

    private void checkWeibo(final CallbackContext context) {
        context.success(this.isWeiboInstalled() ? 1 : 0);
    }

    private boolean isWeiboInstalled() {
        return mSsoHandler != null && mSsoHandler.isWeiboAppInstalled();
    }

    public void init(JSONArray json, final CallbackContext context) {
        appKey = getData(json, "appKey");
        String redirectURI = getData(json, "redirectURI");
        String scope = "all";
        
        Activity activity = this.cordova.getActivity();

        AuthInfo authInfo = new AuthInfo(activity, appKey, redirectURI, scope);

        this.mSsoHandler = new SsoHandler(activity, authInfo);
        context.success(1);
    }

    public void login(final CallbackContext context) {
        Activity activity = this.cordova.getActivity();
        this.cordova.setActivityResultCallback(this);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSsoHandler.authorize(new AuthListener(context));
            }
        });
    }

    public void sendText(String text,final CallbackContext context) throws MalformedURLException, IOException {

        mWeiboShareAPI = WeiboShareSDK.createWeiboAPI(
                this.cordova.getActivity(), appKey);
        mWeiboShareAPI.registerApp();
        WeiboMultiMessage weiboMessage = new WeiboMultiMessage();
        if (text != null) {
            TextObject textObject = new TextObject();
            textObject.text = text;
            weiboMessage.textObject = textObject;
        }
        SendMultiMessageToWeiboRequest request = new SendMultiMessageToWeiboRequest();
        request.transaction = String.valueOf(System.currentTimeMillis());
        request.multiMessage = weiboMessage;
        
        try {
            mWeiboShareAPI.sendRequest(this.cordova.getActivity(), request);
        } catch (Exception e) {
            context.error("this is the end");
        }
        
    }

    // me.sendPage(cfg.getString("page_url"),
    //             cfg.getString("thumb_url"),
    //             cfg.getString("title"),
    //             cfg.getString("desc"),
    //             cfg.getString("text"),
    //             cfg.getString("image_url"));
    public void sendPage(String url, String thumb_url,String title,String desc,String text, String image_url)
            throws MalformedURLException, IOException{

        mWeiboShareAPI = WeiboShareSDK.createWeiboAPI(
                this.cordova.getActivity(), appKey);

        mWeiboShareAPI.registerApp();
        WeiboMultiMessage weiboMessage = new WeiboMultiMessage();

        //分享的网页
        WebpageObject mediaObject = new WebpageObject();
        mediaObject.identify = Utility.generateGUID();

        if(title != null){
            mediaObject.title = title;
        }else{

        }

        if(desc != null){
            mediaObject.description = desc;
        }else{

        }

        mediaObject.actionUrl = url;
        final int THUMBSIZE = 64;
        
        //网页缩略图
        if(thumb_url != null){
            if (!thumb_url.startsWith("data")) {
                Bitmap bmp = null;
                bmp = BitmapFactory.decodeStream(new URL(thumb_url)
                        .openStream());

                Bitmap ThumbImage = ThumbnailUtils.extractThumbnail(bmp, 
                            THUMBSIZE, THUMBSIZE);
                bmp.recycle();
                // 设置 Bitmap 类型的图片到对象里
                mediaObject.setThumbImage(ThumbImage);
            }else if(thumb_url.startsWith("data")){
                String dataUrl = image_url;
                String encodingPrefix = "base64,";
                int contentStartIndex = dataUrl.indexOf(encodingPrefix)
                        + encodingPrefix.length();
                String resData = dataUrl.substring(contentStartIndex);

                byte[] bytes = null;
                try {
                    bytes = Base64.decode(resData, 0);
                } catch (Exception ignored) {
                    Log.e("Weibo", "Invalid Base64 string");
                }

                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                Bitmap ThumbImage = ThumbnailUtils.extractThumbnail(bmp, 
                            THUMBSIZE, THUMBSIZE);
                bmp.recycle();
                // 设置 Bitmap 类型的图片到对象里
                mediaObject.setThumbImage(ThumbImage);
            }
        }

        mediaObject.defaultText = "网页分享默认文字";
        weiboMessage.mediaObject = mediaObject;

        //额外分享的图片
        if (image_url != null) {
            ImageObject imageObject = new ImageObject();
            if (image_url.startsWith("data")) {
                String dataUrl = image_url;
                String encodingPrefix = "base64,";
                int contentStartIndex = dataUrl.indexOf(encodingPrefix)
                        + encodingPrefix.length();
                String resData = dataUrl.substring(contentStartIndex);

                byte[] bytes = null;
                try {
                    bytes = Base64.decode(resData, 0);
                } catch (Exception ignored) {
                    Log.e("Weibo", "Invalid Base64 string");
                }
                imageObject.imageData = bytes;
            } else {
                Bitmap bmp = null;
                bmp = BitmapFactory.decodeStream(new URL(image_url)
                        .openStream());
                imageObject.setImageObject(bmp);
            }
            weiboMessage.imageObject = imageObject;
        }

        //分享的文字
        if (text != null && text.length() > 0) {
            TextObject textObject = new TextObject();
            textObject.text = text;
            weiboMessage.textObject = textObject;
        }

        SendMultiMessageToWeiboRequest request = new SendMultiMessageToWeiboRequest();
        request.transaction = String.valueOf(System.currentTimeMillis());
        request.multiMessage = weiboMessage;
        mWeiboShareAPI.sendRequest(this.cordova.getActivity(), request);
    }

    public void sendImage(String data, String text,final CallbackContext context)
            throws MalformedURLException, IOException {

        String image_path = data;
        mWeiboShareAPI = WeiboShareSDK.createWeiboAPI(
                this.cordova.getActivity(), appKey);
        mWeiboShareAPI.registerApp();
        WeiboMultiMessage weiboMessage = new WeiboMultiMessage();
        if (text != null) {
            TextObject textObject = new TextObject();
            textObject.text = text;
            weiboMessage.textObject = textObject;
        }
        if (image_path != null) {
            ImageObject imageObject = new ImageObject();
            if (image_path.startsWith("data")) {
                String dataUrl = image_path;
                String encodingPrefix = "base64,";
                int contentStartIndex = dataUrl.indexOf(encodingPrefix)
                        + encodingPrefix.length();
                String resData = dataUrl.substring(contentStartIndex);

                byte[] bytes = null;
                try {
                    bytes = Base64.decode(resData, 0);
                } catch (Exception ignored) {
                    Log.e("Weibo", "Invalid Base64 string");
                }
                imageObject.imageData = bytes;
            } else if (image_path.startsWith("http://")) {
                Bitmap bmp = null;
                bmp = BitmapFactory.decodeStream(new URL(image_path)
                        .openStream());
                imageObject.setImageObject(bmp);
            } else {
                imageObject.imagePath = image_path;
            }
            weiboMessage.imageObject = imageObject;
        }
        SendMultiMessageToWeiboRequest request = new SendMultiMessageToWeiboRequest();
        request.transaction = String.valueOf(System.currentTimeMillis());
        request.multiMessage = weiboMessage;
        mWeiboShareAPI.sendRequest(this.cordova.getActivity(), request); 
    }

    public static JSONObject getObjectFromArray(JSONArray jsonArray,
            int objectIndex) {
        JSONObject jsonObject = null;
        if (jsonArray != null && jsonArray.length() > 0) {
            try {
                jsonObject = new JSONObject(jsonArray.get(objectIndex)
                        .toString());
            } catch (JSONException e) {

            }
        }
        return jsonObject;
    }

    public static String getData(JSONArray ary, String key) {
        String result = null;
        try {
            result = getObjectFromArray(ary, 0).getString(key);
        } catch (JSONException e) {

        }
        return result;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (mSsoHandler != null) {
            mSsoHandler.authorizeCallBack(requestCode, resultCode, data);
        }
    }

    class ErrorMessage extends JSONObject {
        public ErrorMessage(int code, String message) {
            try {
                this.put("code", code);
                this.put("message", message);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    class AuthListener implements WeiboAuthListener {
        private CallbackContext context;

        public AuthListener(CallbackContext context) {
            this.context = context;
        }

        @Override
        public void onComplete(Bundle values) {
            Oauth2AccessToken accessToken = Oauth2AccessToken.parseAccessToken(values);
            if (accessToken.isSessionValid()) {
                JSONObject res = new JSONObject();
                try {
                    res.put("uid", accessToken.getUid());
                    res.put("token", accessToken.getToken());
                    res.put("expire_at", accessToken.getExpiresTime());
                    res.put("refresh_token",accessToken.getRefreshToken());
                    context.success(res);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            } else {
                String code = values.getString("code");
                context.error(new ErrorMessage(WEIBO_INVALID_TOKEN, code));
            }
        }

        @Override
        public void onWeiboException(WeiboException e) {
            String message = e.getMessage();
            context.error(new ErrorMessage(WEIBO_UNKNOWN_ERROR, message));
            Log.e(TAG, message, e);
        }

        @Override
        public void onCancel() {
            String message = "authorize cancelled";
            context.error(new ErrorMessage(WEIBO_AUTHORIZE_CANCELED, message));
            Log.i(TAG, message);
        }
    }
}
