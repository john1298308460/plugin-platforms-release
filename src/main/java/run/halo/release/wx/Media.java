package run.halo.release.wx;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(kind = "Media", group = "platforms.release.halo.run",
    version = "v1alpha1", singular = "media", plural = "medias")
public class Media extends AbstractExtension {

    @Schema(requiredMode = REQUIRED)
    private MediaSpec spec;

    @Data
    public static class MediaSpec {

        private String id;

        private String url;

        private String value;

    }
}
