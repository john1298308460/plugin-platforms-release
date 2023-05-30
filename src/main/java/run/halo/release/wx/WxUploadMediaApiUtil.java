package run.halo.release.wx;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

public class WxUploadMediaApiUtil {

    /**
     * 通用接口获取 access token 凭证, 注意需要白名单的 IP 才可调用
     *
     * @param appId
     * @param appSecret
     * @return
     */
    public static Token getToken(String appId, String appSecret) {
        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse response = null;
        Token token = null;
        JSONObject jsonObject = new JSONObject();
        try {
            String requestUrl =
                ConstantUtil.tokenUrl.replace("APPID", appId).replace("APPSECRET", appSecret);
            HttpGet httpGet = new HttpGet(requestUrl);
            response = client.execute(httpGet);
            HttpEntity responseEntity = response.getEntity();
            String result = EntityUtils.toString(responseEntity, Charset.forName("UTF-8"));
            jsonObject = JSON.parseObject(result);
            if (null != jsonObject) {
                token = new Token();
                token.setAccessToken(jsonObject.getString("access_token"));
                token.setExpiresIn(jsonObject.getIntValue("expires_in"));
            }
            return token;
        } catch (Exception e) {
            // 获取token失败
            System.out.println(
                "获取 token 失败 errcode:" + jsonObject.getIntValue("errcode"));
            System.out.println("获取 token 失败 errmsg:" + jsonObject.getString("errmsg"));
            e.printStackTrace();
            return null;
        } finally {
            // 关闭连接和流
            try {
                if (client != null) {
                    client.close();
                }
                if (response != null) {
                    response.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * 将图片转为file
     *
     * @param url 图片url
     * @return File
     * @author dyc
     * date:   2020/9/4 14:54
     */
    private static File getFile(String url) {
        // 获取文件名
        String prefixName = url.substring(url.lastIndexOf("/") + 1);
        File file = null;
        URL urlfile;
        InputStream inStream = null;
        OutputStream os = null;
        try {
            file = new File(prefixName);
            urlfile = new URL(url);
            inStream = urlfile.openStream();
            os = new FileOutputStream(file);
            int bytesRead = 0;
            byte[] buffer = new byte[8192];
            while ((bytesRead = inStream.read(buffer, 0, 8192)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (null != os) {
                    os.close();
                }
                if (null != inStream) {
                    inStream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return file;
    }


    /**
     * 上传图片到微信公众号永久素材库中
     * @param imgUrl
     * @param accessToken
     * @return
     */
    public static JSONObject uploadimg(String imgUrl, String accessToken) {
        String uploadUrl = String.format(ConstantUtil.uploadUrl, accessToken, "image");
        // 创建客户端连接对象
        CloseableHttpClient client = HttpClients.createDefault();
        String result = "";
        try {
            File file = getFile(imgUrl);
            HttpPost httpPost = new HttpPost(uploadUrl);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.LEGACY);
            // 上传多媒体文件
            builder.addBinaryBody("media", file);
            HttpEntity entity = builder.build();
            httpPost.setEntity(entity);
            // 执行提交
            CloseableHttpResponse response = client.execute(httpPost);
            HttpEntity responseEntity = response.getEntity();
            result = EntityUtils.toString(responseEntity, Charset.forName("UTF-8"));
            JSONObject json = JSON.parseObject(result);
            // 删除临时文件
            file.delete();
            return json;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * 根据 html 文本，将 html 内的图片上传至微信公众号素材库，并且将文本中的 src 替换成返回的 url
     * @param content
     * @param accessToken
     * @return
     * @throws IOException
     */
    public static String getImgs(String content, String accessToken) {
        try {
            String img = "";
            Pattern p_image;
            Matcher m_image;
            String regEx_img = "(<img.*src\\s*=\\s*(.*?)[^>]*?>)";
            p_image = Pattern.compile(regEx_img, Pattern.CASE_INSENSITIVE);
            m_image = p_image.matcher(content);
            while (m_image.find()) {
                img = m_image.group();
                Matcher m = Pattern.compile("src\\s*=\\s*\"?(.*?)(\"|>|\\s+)").matcher(img);
                while (m.find()) {
                    // 匹配到第一个 src 的内容
                    String tempSelected = m.group(1);
                    // src 的值取出来，调用图片上传到微信素材库的接口, 遍历存储文章中的图片，并且将返回的 URL 以及 media_id 存入自定义模型，并且替换 html 中的 src 值为返回的 url
                    JSONObject rst = uploadimg(tempSelected, accessToken);
                    if (rst != null) {
                        String wxImgUrl = rst.getString("url");
                        String wxImgMediaId = rst.getString("media_id");
                        // TODO 存自定义模型
                        // 将匹配到的内容替换成返回的微信图片对应 url,否则后续上传的文章图片无法在公众号识别
                        content = content.replaceFirst(tempSelected, wxImgUrl);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return content;
    }


    /**
     * 新增内容到草稿箱
     * @param title
     * @param author
     * @param content
     * @return 草稿发布后的标识 MEDIA_ID
     */
    public static String addDraft(String title, String author, String content, String accessToken,
                                  String thumbMediaId) {
        String mediaId = "";
        // 开始调用微信公众号的 api：发布草稿
        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;
        try {
            String url = ConstantUtil.sendtemplateUrl.replace("ACCESS_TOKEN", accessToken);
            // 创建客户端连接对象
            client = HttpClients.createDefault();
            // 构建 Post 请求对象
            HttpPost post = new HttpPost(url);
            // 设置传送的内容类型是 json 格式
            post.setHeader("Content-Type", "application/json;charset=utf-8");
            // 接收的内容类型也是 json 格式
            post.setHeader("Accept", "application/json;charset=utf-8");
            // 设置超时时间，其中 connectionRequestTimout（从连接池获取连接的超时时间）、connetionTimeout（客户端和服务器建立连接的超时时间）、socketTimeout（客户端从服务器读取数据的超时时间），单位都是毫秒
            RequestConfig config =
                RequestConfig.custom().setConnectTimeout(Timeout.ofSeconds(10))
                    .setConnectionRequestTimeout(Timeout.ofSeconds(3)).build();
            post.setConfig(config);
            // 准备数据
            JSONObject jsonArray = new JSONObject();
            jsonArray.put("title", title);
            jsonArray.put("author", author);
            jsonArray.put("content", content);
            jsonArray.put("thumb_media_id", thumbMediaId);
            jsonArray.put("need_open_comment", 0);
            jsonArray.put("only_fans_can_comment", 0);
            // 设置请求体
            String article = "{ \"articles\":[ " + jsonArray + "]}";
            post.setEntity(new StringEntity(article, Charset.forName("UTF-8")));
            // 获取返回对象
            response = client.execute(post);
            // 整理返回值
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            // 转换为json格式
            JSONObject rst = JSON.parseObject(result);
            // 获取 json 中的 media_id
            mediaId = rst.getString("media_id");
        } catch (SocketTimeoutException e) {
            e.printStackTrace();
            throw new RuntimeException("获取token出现连接/超时异常");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("获取token时执行内部代码时出现异常");
        } finally {
            try {
                if (client != null) {
                    client.close();
                }
                if (response != null) {
                    response.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return mediaId.toString();
    }

}
