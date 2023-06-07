package run.platforms.release;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
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
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import run.halo.release.wx.ConstantUtil;
import run.halo.release.wx.Token;

@ExtendWith(MockitoExtension.class)
public class HttpMain {

    /**
     * 获取 access_token
     *
     * @param appid
     * @param appsecret
     * @return
     */
    public static Token getToken(String appid, String appsecret)
        throws IOException, ParseException {
        Token token = null;
        String requestUrl =
            ConstantUtil.tokenUrl.replace("APPID", appid).replace("APPSECRET", appsecret);
        // 发起GET请求获取凭证
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(requestUrl);
        CloseableHttpResponse response = client.execute(httpGet);
        HttpEntity responseEntity = response.getEntity();
        String result = EntityUtils.toString(responseEntity, Charset.forName("UTF-8"));
        JSONObject jsonObject = JSON.parseObject(result);
        if (null != jsonObject) {
            try {
                token = new Token();
                token.setAccessToken(jsonObject.getString("access_token"));
                token.setExpiresIn(jsonObject.getIntValue("expires_in"));
            } catch (JSONException e) {
                token = null;
                // 获取token失败
                System.out.println(
                    "获取token失败 errcode:" + jsonObject.getIntValue("errcode"));
                System.out.println("获取token失败 errmsg:" + jsonObject.getString("errmsg"));
            }
        }
        return token;
    }


    /**
     * 将图片转为file
     *
     * @param url 图片url
     * @return File
     * @author dyc
     * date:   2020/9/4 14:54
     */
    private static File getFile(String url) throws Exception {
        //对本地文件命名
//        String fileName = url.substring(url.lastIndexOf("."), url.length());
        String prefixName = url.substring(url.lastIndexOf("/") + 1);
        File file = null;
        URL urlfile;
        InputStream inStream = null;
        OutputStream os = null;
        try {
//            file = File.createTempFile("net_url", fileName);
            //file.renameTo(new File(prefixName));
            file = new File(prefixName);
            //下载
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
     * @throws IOException
     */
    public JSONObject uploadimg(String imgUrl, String accessToken) throws Exception {
        File file = getFile(imgUrl);
        String uploadUrl = String.format(ConstantUtil.uploadUrl, accessToken, "image");
        // 创建客户端连接对象
        CloseableHttpClient client = HttpClients.createDefault();
        String result = "";
        try {
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
     * 新增内容到草稿箱
     * @param title
     * @param author
     * @param content
     * @return 草稿发布后的标识 MEDIA_ID
     */
    public String addDraft(String title, String author, String content, String accessToken,
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
            // 设置超时时间，其中connectionRequestTimout（从连接池获取连接的超时时间）、connetionTimeout（客户端和服务器建立连接的超时时间）、socketTimeout（客户端从服务器读取数据的超时时间），单位都是毫秒
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
            response = client.execute(post);
            // 整理返回值
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            JSONObject rst = JSON.parseObject(result);
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
        return mediaId;
    }


    // 编辑草稿箱
    public int updateDraft(String title, String author, String content, String accessToken,
                           String thumbMediaId, String mediaId) {
        // 开始调用微信公众号的 api：发布草稿
        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;
        try {
            String url = ConstantUtil.updateDraftUrl.replace("ACCESS_TOKEN", accessToken);
            client = HttpClients.createDefault();
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json;charset=utf-8");
            post.setHeader("Accept", "application/json;charset=utf-8");
            RequestConfig config = RequestConfig.custom().setConnectTimeout(Timeout.ofSeconds(10))
                .setConnectionRequestTimeout(Timeout.ofSeconds(3)).build();
            post.setConfig(config);
            // 准备数据
            JSONObject jsonArray = new JSONObject();
            JSONObject articles = new JSONObject();

            jsonArray.put("media_id", mediaId);
            // 要更新的文章在图文消息中的位置（多图文消息时，此字段才有意义），第一篇为0
            jsonArray.put("index", 0);
            articles.put("title", title);
            articles.put("author", author);
            articles.put("content", content);
            articles.put("thumb_media_id", thumbMediaId);
            articles.put("need_open_comment", 0);
            articles.put("only_fans_can_comment", 0);

            jsonArray.put("articles", articles);
            // 设置请求体
            post.setEntity(new StringEntity(jsonArray.toString(), Charset.forName("UTF-8")));
            response = client.execute(post);
            // 整理返回值
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            JSONObject rst = JSON.parseObject(result);

            return rst.getInteger("errcode");
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
    }


    /**
     * 删除草稿接口
     * @param accessToken
     * @param mediaId
     * @return
     */
    public int deleteDraft(String accessToken, String mediaId) {
        // 开始调用微信公众号的 api：发布草稿
        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;
        try {
            String url = ConstantUtil.deleteDraftUrl.replace("ACCESS_TOKEN", accessToken);
            client = HttpClients.createDefault();
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json;charset=utf-8");
            post.setHeader("Accept", "application/json;charset=utf-8");
            RequestConfig config = RequestConfig.custom().setConnectTimeout(Timeout.ofSeconds(10))
                .setConnectionRequestTimeout(Timeout.ofSeconds(3)).build();
            post.setConfig(config);
            // 准备数据
            JSONObject json = new JSONObject();
            json.put("media_id", mediaId);
            // 设置请求体
            post.setEntity(new StringEntity(json.toString(), Charset.forName("UTF-8")));
            response = client.execute(post);
            // 整理返回值
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            JSONObject rst = JSON.parseObject(result);
            return rst.getInteger("errcode");
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
    }


    public String getImgUrlByMediaId(String mediaId, String accessToken) {
        // 创建客户端连接对象
        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse response = null;
        String result = "";
        try {
            String url = ConstantUtil.getMediaUrl.replace("ACCESS_TOKEN", accessToken);
            HttpPost post = new HttpPost(url);
            // 设置传送的内容类型是 json 格式
            post.setHeader("Content-Type", "application/json;charset=utf-8");
            // 接收的内容类型也是 json 格式
            post.setHeader("Accept", "application/json;charset=utf-8");

            // 设置超时时间，其中connectionRequestTimout（从连接池获取连接的超时时间）、connetionTimeout（客户端和服务器建立连接的超时时间）、socketTimeout（客户端从服务器读取数据的超时时间），单位都是毫秒
            RequestConfig config =
                RequestConfig.custom().setConnectTimeout(Timeout.ofSeconds(10))
                    .setConnectionRequestTimeout(Timeout.ofSeconds(3)).build();
            post.setConfig(config);
            // 准备数据
            JSONObject json = new JSONObject();
            json.put("media_id", mediaId);
            // 设置请求体
            post.setEntity(new StringEntity(json.toString(), Charset.forName("UTF-8")));
            // 获取返回对象
            response = client.execute(post);
            response.setHeader("Content-Type", "application/json;charset=utf-8");
            // 整理返回值
            HttpEntity entity = response.getEntity();
            result = EntityUtils.toString(entity);
            //转换为JSON格式
            JSONObject rstObj = JSON.parseObject(result);
            //获取文章发布以后返回的publish_id
            String wxImgUrl = rstObj.getString("url");
            return wxImgUrl;
        } catch (IOException e) {
            e.printStackTrace();
            return "出现IO错误，未能获取url";
        } catch (Exception e) {
            e.printStackTrace();
            return "出现未知错误，未能获取url";
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


    /**
     * 根据 media_id 发布文章
     * @param mediaId
     * @param accessToken
     * @return
     */
    public String publishPost(String mediaId, String accessToken) {
        String publishId = "";
        // 自定义模型 API 根据素材名查找对应的 media_id 和 url
        CloseableHttpResponse response = null;
        // 创建客户端连接对象
        CloseableHttpClient client = HttpClients.createDefault();

        String publishUrl = ConstantUtil.publishUrl.replace("ACCESS_TOKEN", accessToken);
        try {

            // 构建Post请求对象
            HttpPost post = new HttpPost(publishUrl);
            // 设置传送的内容类型是json格式
            post.setHeader("Content-Type", "application/json;charset=utf-8");
            // 接收的内容类型也是json格式
            post.setHeader("Accept", "application/json;charset=utf-8");

            RequestConfig config =
                RequestConfig.custom().setConnectTimeout(Timeout.ofSeconds(10))
                    .setConnectionRequestTimeout(Timeout.ofSeconds(3)).build();
            post.setConfig(config);
            // 准备数据
            JSONObject json = new JSONObject();
            json.put("media_id", mediaId);
            // 设置请求体
            post.setEntity(new StringEntity(json.toString(), Charset.forName("UTF-8")));
            // 获取返回对象
            response = client.execute(post);

            // 整理返回值
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            //转换为JSON格式
            JSONObject rstObj = JSON.parseObject(result);

            publishId = rstObj.getString("publish_id");

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

            return publishId;
        }
    }


    /**
     * 根据 html 文本，将 html 内的图片上传至微信公众号素材库，并且将文本中的 src 替换成返回的 url
     * @param content
     * @param accessToken
     * @return
     * @throws IOException
     */
    private String getImgs(String content, String accessToken) throws Exception {
        String img = "";
        Pattern p_image;
        Matcher m_image;
        String str = "";
        String[] images = null;
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

                // 原始文章内 src 没有加前缀，需要加前缀才能上传
                // the uriStr is encoded before.
                System.out.println("图片的完整路径为： " + tempSelected);
                JSONObject rst = this.uploadimg(tempSelected, accessToken);
                if (rst != null) {
                    String wxImgUrl = rst.getString("url");
                    String wxImgMediaId = rst.getString("media_id");
                    // 将匹配到的内容替换成返回的微信图片对应 url,否则后续上传的文章图片无法在公众号识别
                    content = content.replaceFirst(tempSelected, wxImgUrl);
                }

            }
        }
        return content;
    }


    /**
     * 群发接口
     * @param mediaId   草稿素材 ID
     * @param thumbMediaId  缩略图 ID
     * @param accessToken   接口凭证
     * @return
     */
    public String sendAllMessage(String mediaId, String thumbMediaId, String accessToken) {

        String msgDataId = "";
        CloseableHttpResponse response = null;
        // 创建客户端连接对象
        CloseableHttpClient client = HttpClients.createDefault();
        String publishUrl = ConstantUtil.getSendAllUrl.replace("ACCESS_TOKEN", accessToken);
        try {

            // 构建Post请求对象
            HttpPost post = new HttpPost(publishUrl);
            // 设置传送的内容类型是json格式
            post.setHeader("Content-Type", "application/json;charset=utf-8");
            // 接收的内容类型也是json格式
            post.setHeader("Accept", "application/json;charset=utf-8");

            RequestConfig config =
                RequestConfig.custom().setConnectTimeout(Timeout.ofSeconds(10))
                    .setConnectionRequestTimeout(Timeout.ofSeconds(3)).build();
            post.setConfig(config);

            // 准备数据
            JSONObject param = new JSONObject();
            JSONObject filter = new JSONObject();

            filter.put("is_to_all", true); //是否发送所有人
            param.put("filter", filter);
            JSONObject mpnews = new JSONObject();
            mpnews.put("media_id", mediaId); //图文消息media_id
            param.put("mpnews", mpnews);
            param.put("msgtype", "mpnews"); //类型，mpnews为图文消息
            param.put("send_ignore_reprint", 0); //图文消息被判定为转载时，是否继续群发。 1为继续群发（转载），0为停止群发。 该参数默认为0。
            param.put("thumb_media_id", thumbMediaId);


            // 设置请求体
            post.setEntity(new StringEntity(param.toString(), Charset.forName("UTF-8")));
            // 获取返回对象
            response = client.execute(post);

            // 整理返回值
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            //转换为JSON格式
            JSONObject rstObj = JSON.parseObject(result);

            msgDataId = rstObj.getString("msg_data_id");

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

            return msgDataId;
        }

    }









    @Test
    public void wxTest() throws Exception {
        String appid_value = "";
        String secret_value = "";
        String thumbMediaId = "";
        //Token token = getToken(appid_value, secret_value);
        //获取access_token的json
        //String access_token = token.getAccessToken();
        // 上传封面图
        //JSONObject uploadImgRst = uploadimg("http://localhost:8090/upload/0605_1.png", access_token);
//        if (uploadImgRst != null) {
//            thumbMediaId = uploadImgRst.getString("media_id");
//        }
        String title = "测试文章";
        String author = "测试用户";
        String content =
            "<h2 id=\"heading-1\"><img loading=\"lazy\" src=\"http://localhost:8090/upload/0605_1.png\" alt=\"布鲁.jpg\" width=\"100%\" height=\"100%\" style=\"display: inline-block\"><strong>Hello Halo</strong></h2><p>如果你看到了这一篇文章，那么证明你已经安装成功了，感谢使用 <a target=\"_blank\" rel=\"noopener noreferrer nofollow\" href=\"https://halo.run/\">Halo</a> 进行创作，希望<strong>能够</strong>使用愉快。</p><h2 id=\"heading-2\"><strong>相关链接</strong></h2><ul><li><p>官网：<a target=\"_blank\" rel=\"noopener noreferrer nofollow\" href=\"https://halo.run\">https://halo.run</a></p></li><li><p>文档：<a target=\"_blank\" rel=\"noopener noreferrer nofollow\" href=\"https://docs.halo.run\">https://docs.halo.run</a></p></li><li><p>社区：<a target=\"_blank\" rel=\"noopener noreferrer nofollow\" href=\"https://bbs.halo.run\">https://bbs.halo.run</a></p></li><li><p>主题仓库：<a target=\"_blank\" rel=\"noopener noreferrer nofollow\" href=\"https://halo.run/themes.html\">https://halo.run/themes.html</a></p></li><li><p>开源地址：<a target=\"_blank\" rel=\"noopener noreferrer nofollow\" href=\"https://github.com/halo-dev/halo\">https://github.com/halo-dev/halo</a></p></li></ul><img loading=\"lazy\" src=\"http://localhost:8090/upload/0605_1.png\" alt=\"布鲁.jpg\" width=\"100%\" height=\"100%\" style=\"display: inline-block\"><p>在使用过程中，有任何问题都可以通过以上链接找寻答案，或者联系我们。</p><blockquote><p>这是一篇自动生成的文章，请删除这篇文章之后开始你的创作吧！</p></blockquote>";
        // 上传图片并且替换 url
        //String content2 = getImgs(content, access_token);
        // 编辑草稿箱
        String mediaId = "";
        // 编辑封面图
        //Integer rst = this.updateDraft(title, author, content2, access_token, thumbMediaId, mediaId);
        // 上传到草稿
        //String rst = addDraft(title, author, content2, access_token, thumbMediaId);
        // 删除草稿
        //int rst = deleteDraft(access_token, mediaId);
        // 发布草稿
//        String publishId = publishPost(rst, access_token);
        // 群发推送
//        String msgDataId = sendAllMessage(rst, thumbMediaId, access_token);
        //System.out.println(rst);
    }
}
