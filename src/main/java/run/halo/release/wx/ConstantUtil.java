package run.halo.release.wx;

public class ConstantUtil {

    // 凭证获取（GET）
    public final static String tokenUrl =
        "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=APPID&secret=APPSECRET";

    // 获取用户openid集合
    public final static String sendResourceUrl =
        "https://api.weixin.qq.com/cgi-bin/material/batchget_material?access_token=ACCESS_TOKEN";

    // 上传素材
    public final static String uploadUrl =
        "https://api.weixin.qq.com/cgi-bin/material/add_material?access_token=%s&type=%s";

    // 发布
    public final static String publishUrl =
        "https://api.weixin.qq.com/cgi-bin/freepublish/submit?access_token=ACCESS_TOKEN";

    // 新增草稿
    public final static String sendtemplateUrl =
        "https://api.weixin.qq.com/cgi-bin/draft/add?access_token=ACCESS_TOKEN";

    // 编辑草稿
    public final static String updateDraftUrl =
        "https://api.weixin.qq.com/cgi-bin/draft/update?access_token=ACCESS_TOKEN";

    // 删除草稿
    public final static String deleteDraftUrl =
        "https://api.weixin.qq.com/cgi-bin/draft/delete?access_token=ACCESS_TOKEN";

    // 获取草稿
    public final static String getDraftUrl =
        "https://api.weixin.qq.com/cgi-bin/draft/get?access_token=ACCESS_TOKEN";


    // 根据 mediaId 获取素材
    public static final String getMediaUrl =
        "https://api.weixin.qq.com/cgi-bin/material/get_material?access_token=ACCESS_TOKEN";

    // 公众号群发消息（标签模式）
    public static final String getSendAllUrl =
        "https://api.weixin.qq.com/cgi-bin/message/mass/sendall?access_token=ACCESS_TOKEN";
}
