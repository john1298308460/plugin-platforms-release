package run.halo.release;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.Snapshot;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.halo.release.wx.Media;
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
                try {
                    var annotations = post.getMetadata().getAnnotations();
                    if (annotations != null) {
                        // 获取当前的文章状态
                        Boolean postStatus = post.isPublished();
                        Optional<ConfigMap> configMapOptional = client.fetch(ConfigMap.class, "platforms-settings");
                        if (configMapOptional != null && configMapOptional.get().getData().get("basic") != null) {
                            // 是否发布到微信公众号的标识
                            JSONObject jsonObject = JSON.parseObject(configMapOptional.get().getData().get("basic"));
                            // 获取微信公众号配置
                            String appId = jsonObject.getString("appId");
                            String appSecret = jsonObject.getString("appSecret");
                            // 获取接口凭证 accessToken
                            Token token = WxUploadMediaApiUtil.getToken(appId, appSecret);
                            String accessToken = token.getAccessToken();
                            // 获取草稿自定义模型，判断草稿是否已存在
                            Optional<Media> media = client.fetch(Media.class, post.getMetadata().getName());
                            // 如果上一次插件保存的文章发布状态为 false, 然后获取当前的即时文章发布状态为 true 的话，则代表文章是从未发布转发布状态，执行逻辑
                            if (annotations.get("if_publish") != null &&
                                annotations.get("if_publish").equals("true") &&
                                annotations.get("postStatus") != null &&
                                annotations.get("postStatus").equals("false") &&
                                post.isPublished()) {
                                // 封面图 media_id
                                String thumbMediaId = "";
                                // 判断是否已经上传过封面图，主键就是 Halo 的图片 url 路径
                                Optional<Media> coverMedia = client.fetch(Media.class, post.getSpec().getCover());
                                if (coverMedia.isEmpty()) {
                                    // 还未存储过封面图，接口请求 thumbMediaId
                                    // 上传封面图
                                    JSONObject uploadCoverImg = WxUploadMediaApiUtil.uploadimg(post.getSpec().getCover(), accessToken);
                                    // 获取封面图 mediaID
                                    thumbMediaId = uploadCoverImg.getString("media_id");
                                    // 封面图需要存入自定义模型
                                    // spec.id 封面图微信返回 media_id; spec.value 封面图微信返回 url; metadata.name 封面图 Halo 存储路径
                                    Media mediaItem = new Media();
                                    Media.MediaSpec mediaSpec = new Media.MediaSpec();
                                    mediaSpec.setId(thumbMediaId);
                                    mediaSpec.setValue(uploadCoverImg.getString("url"));
                                    mediaItem.setMetadata(new Metadata());
                                    mediaItem.getMetadata().setName(post.getSpec().getCover());
                                    mediaItem.setSpec(mediaSpec);
                                    client.create(mediaItem);
                                } else {
                                    // 非空，之前已经上传过封面图
                                    thumbMediaId = coverMedia.get().getSpec().getId();
                                }
                                // 获取标题
                                String title = post.getSpec().getTitle();
                                // 获取作者
                                String owner = post.getSpec().getOwner();
                                // 获取文章内容
                                String content = getContent(post);
                                // 上传图片并且替换 url
                                WxUploadMediaApiUtil wxUploadMediaApiUtil = new WxUploadMediaApiUtil(client);
                                // 获取覆盖过 wx 图片 url 之后的正文
                                String coverContent = wxUploadMediaApiUtil.getImgs(content, accessToken);
                                // 此状态是从未发布 转向 发布状态。判断是否文章需要编辑还是新增草稿
                                // 查询自定义模型是否有数据，有则编辑，无则新增
                                if (!media.isEmpty() && media.get().getSpec().getId() != null) {
                                    // 之前已经存过 media, 这里走编辑草稿的逻辑
//                                    System.out.println("草稿自定义模型 media_id： " + media.get().getSpec().getId());
//                                    System.out.println("这里是执行编辑文章的逻辑");
                                    Integer errcode = WxUploadMediaApiUtil.updateDraft(title, owner, coverContent, accessToken, thumbMediaId, media.get().getSpec().getId());
                                    System.out.println("修改草稿返回状态值为： " + errcode);
                                    if (errcode != 0) {
                                        throw new Exception("微信公众号修改草稿失败");
                                    }
                                } else {
                                    // 执行新增草稿逻辑
                                    // 上传到草稿箱
                                    String draftMediaId = WxUploadMediaApiUtil.addDraft(title, owner, coverContent, accessToken, thumbMediaId);
                                    // 新增草稿之后，自定义模型记录 草稿ID 和 文章主键 ID
                                    Media mediaItem = new Media();
                                    Media.MediaSpec mediaSpec = new Media.MediaSpec();
                                    mediaSpec.setId(draftMediaId);
                                    mediaItem.setMetadata(new Metadata());
                                    // 文章主键 ID
                                    mediaItem.getMetadata().setName(post.getMetadata().getName());
                                    mediaItem.setSpec(mediaSpec);
                                    client.create(mediaItem);
                                    System.out.println("文章已经上传草稿箱，返回 mediaId： " + draftMediaId);
                                }
                            }
                            // 判断文章是否删除逻辑，需要开启了同步微信公众号才生效
                            if (post.getSpec().getDeleted() && !media.isEmpty() && media.get().getSpec().getId() != null
                                && annotations.get("if_publish") != null
                                && annotations.get("if_publish").equals("true")) {
//                                System.out.println("准备删除公众号草稿");
                                // 需要把草稿自定义模型也删除
                                int errorStatus = WxUploadMediaApiUtil.deleteDraft(accessToken, media.get().getSpec().getId());
                                if (errorStatus == 0) {
                                    // 状态为 0 则是删除成功, 删除自定义模型
                                    client.delete(media.get());
                                    System.out.println("草稿自定义模型删除成功");
                                }
                            }
                        }
                        annotations.put("postStatus", String.valueOf(postStatus));
                        post.getMetadata().setAnnotations(annotations);
                        // 保存文章的状态，便于插件下次获取
                        client.update(post);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
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
        Optional<Snapshot> releasedSnapshotOpt = client.fetch(Snapshot.class, releaseSnapshot);
        Optional<Snapshot> baseSnapshotOpt = client.fetch(Snapshot.class, baseSnapshotName);
        if (StringUtils.equals(releaseSnapshot, baseSnapshotName)) {
            // 是一样的直接查询返回内容就好了 否则查询两个 patch 一下
            return releasedSnapshotOpt.get().getSpec().getContentPatch();
        }
        String patchedContent = PatchUtils.applyPatch(baseSnapshotOpt.get().getSpec().getContentPatch(), releasedSnapshotOpt.get().getSpec().getContentPatch());
        return patchedContent;
    }

}
