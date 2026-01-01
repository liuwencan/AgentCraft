package cn.lwccxy.agentcraft.agent.skills.manual.config;

import cn.lwccxy.agentcraft.agent.skills.manual.model.SkillMetadata;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 技能配置类。
 *
 * 通过 Builder API 配置文件系统技能目录，支持多个目录组合。
 * 后添加的目录会覆盖先添加的目录中的同名技能。
 *
 * @author rel
 * 
 */
public class SkillConfig {

    private final List<Path> directories;
    private final Function<List<SkillMetadata>, String> promptFormatter;
    private final boolean autoReload;

    private SkillConfig(Builder builder) {
        this.directories = List.copyOf(builder.directories);
        this.promptFormatter = builder.promptFormatter;
        this.autoReload = builder.autoReload;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Path> getDirectories() {
        return directories;
    }

    public Function<List<SkillMetadata>, String> getPromptFormatter() {
        return promptFormatter;
    }

    public boolean isAutoReload() {
        return autoReload;
    }

    public static class Builder {
        private final List<Path> directories = new ArrayList<>();
        private Function<List<SkillMetadata>, String> promptFormatter = null;
        private boolean autoReload = false;

        public Builder addDirectory(String path) {
            return addDirectory(Path.of(path));
        }

        public Builder addDirectory(Path path) {
            Objects.requireNonNull(path, "path must not be null");
            this.directories.add(path);
            return this;
        }

        public Builder promptFormatter(Function<List<SkillMetadata>, String> formatter) {
            this.promptFormatter = formatter;
            return this;
        }

        public Builder autoReload(boolean autoReload) {
            this.autoReload = autoReload;
            return this;
        }

        public SkillConfig build() {
            return new SkillConfig(this);
        }
    }
}
