package run.halo.release;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.Snapshot;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.halo.release.wx.Token;
import run.halo.release.wx.WxUploadMediaApiUtil;

/**
 * 监听文章发布时的变化，执行业务逻辑
 */
@AllArgsConstructor
@Component
public class PostPublishReconciler implements Reconciler<Reconciler.Request> {

    private final ExtensionClient client;

    @Override
    public Result reconcile(Request request) {
        client.fetch(Post.class, request.name())
            .ifPresent(post -> {
//                System.out.println("进入插件钩子函数 post 的发布状态" + post.isPublished());
                var annotations = post.getMetadata().getAnnotations();
                if (annotations != null) {
                    // 获取当前的文章状态
                    Boolean postStatus = post.isPublished();
//                    System.out.println("if_publish 值为：" + annotations.get("if_publish"));
//                    System.out.println("postStatus 值为：" + annotations.get("postStatus"));
//                    System.out.println("isPublished 值为：" + post.isPublished());
                    // 如果上一次插件保存的文章发布状态为 false, 然后获取当前的即时文章发布状态为 true 的话，则代表文章是从未发布转发布状态，执行逻辑
                    if (annotations.get("if_publish").equals("true") &&
                        annotations.get("postStatus") != null &&
                        annotations.get("postStatus").equals("false") && post.isPublished()) {
                        Optional<ConfigMap> configMapOptional =
                            client.fetch(ConfigMap.class, "platforms-settings");
                        // 是否发布到微信公众号的标识
//                        System.out.println("是否发布到微信公众号的标识" + annotations.get("if_publish"));
//                        System.out.println("文章封面地址：" + post.getSpec().getCover());
                        if (configMapOptional.get().getData().get("basic") != null) {
                            JSONObject jsonObject =
                                JSON.parseObject(configMapOptional.get().getData().get("basic"));
                            // 获取微信公众号配置
                            String appId = jsonObject.getString("appId");
                            String appSecret = jsonObject.getString("appSecret");
                            // 获取接口凭证 accessToken
                            Token token = WxUploadMediaApiUtil.getToken(appId, appSecret);
                            String accessToken = token.getAccessToken();
                            // 上传封面图
                            JSONObject uploadCoverImg =
                                WxUploadMediaApiUtil.uploadimg(post.getSpec().getCover(),
                                    accessToken);
                            // 获取封面图 mediaID
                            String thumbMediaId = uploadCoverImg.getString("media_id");
                            // 获取标题
                            String title = post.getSpec().getTitle();
                            // 获取作者
                            String owner = post.getSpec().getOwner();
                            // 获取文章内容
                            String content = getContent(post);
                            // 上传图片并且替换 url
                            String coverContent = WxUploadMediaApiUtil.getImgs(content, accessToken);
                            // 上传到草稿箱
                            String draftMediaId = WxUploadMediaApiUtil.addDraft(title, owner, coverContent, accessToken, thumbMediaId);
                            System.out.println("文章已经上传草稿箱，返回 mediaId： " + draftMediaId);
                        }
                    }
                    annotations.put("postStatus", String.valueOf(postStatus));
                    post.getMetadata().setAnnotations(annotations);
                    // 保存文章的状态，便于插件下次获取
                    client.update(post);
                }
            });
        return new Result(false, null);
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder
            .extension(new Post())
            .build();
    }


    // 获取文章内容
    public String getContent(Post post) {
        String releaseSnapshot = post.getSpec().getReleaseSnapshot();
        String baseSnapshotName = post.getSpec().getBaseSnapshot();
        Optional<Snapshot> releasedSnapshotOpt =
            client.fetch(Snapshot.class, releaseSnapshot);
        Optional<Snapshot> baseSnapshotOpt =
            client.fetch(Snapshot.class, baseSnapshotName);
        String patchedContent =
            PatchUtils.applyPatch(baseSnapshotOpt.get().getSpec().getContentPatch(),
                releasedSnapshotOpt.get().getSpec().getContentPatch());
        return patchedContent;
    }

}
