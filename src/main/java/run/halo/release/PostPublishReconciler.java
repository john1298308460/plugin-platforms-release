package run.halo.release;

import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;

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
                System.out.println("进入插件钩子函数 post 的发布状态" + post.isPublished());
                var annotations = post.getMetadata().getAnnotations();
                if (annotations != null) {
                    // 获取当前的文章状态
                    Boolean postStatus = post.isPublished();
                    System.out.println("if_publish 值为：" + annotations.get("if_publish"));
                    System.out.println("postStatus 值为：" + annotations.get("postStatus"));
                    System.out.println("isPublished 值为：" + post.isPublished());
                    // 如果上一次插件保存的文章发布状态为 false, 然后获取当前的即时文章发布状态为 true 的话，则代表文章是从未发布转发布状态，执行逻辑
                    if (annotations.get("if_publish").equals("true") &&
                        annotations.get("postStatus") != null &&
                        annotations.get("postStatus").equals("false") && post.isPublished()) {
                        Optional<ConfigMap> configMapOptional =
                            client.fetch(ConfigMap.class, "platforms-settings");
                        System.out.println(
                            "微信公众号配置：" + configMapOptional.get().getData().get("basic"));
                        // 是否发布到微信公众号的标识
                        System.out.println(
                            "是否发布到微信公众号的标识" + annotations.get("if_publish"));


                        // TODO, 执行业务逻辑


                        System.out.println("这里执行插件的业务逻辑！");
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

}
