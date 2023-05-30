package run.halo.release;

import org.pf4j.PluginWrapper;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;
import run.halo.release.wx.Media;

/**
 * @author junhong
 * @since 2.0.0
 */
@Component
public class PlatformsReleasePlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public PlatformsReleasePlugin(PluginWrapper wrapper, SchemeManager schemeManager) {
        super(wrapper);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        // 插件启动时注册自定义模型
        schemeManager.register(Media.class);
        System.out.println("插件启动成功！");
    }

    @Override
    public void stop() {
        // 插件停用时取消注册自定义模型
        Scheme mediaScheme = schemeManager.get(Media.class);
        schemeManager.unregister(mediaScheme);
        System.out.println("插件停止！");
    }
}
