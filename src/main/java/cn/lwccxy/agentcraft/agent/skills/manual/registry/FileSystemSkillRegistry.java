package cn.lwccxy.agentcraft.agent.skills.manual.registry;

import cn.lwccxy.agentcraft.agent.skills.manual.model.SkillLoadingException;
import cn.lwccxy.agentcraft.agent.skills.manual.model.SkillMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 文件系统技能注册表。
 *
 * 从本地文件系统加载技能。支持目录扫描，自动发现
 * 技能目录中的 SKILL.md 文件。
 *
 * @author rel
 * 
 */
public class FileSystemSkillRegistry extends AbstractSkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(FileSystemSkillRegistry.class);

    private static final String SKILL_MD_FILE = "SKILL.md";

    private final List<Path> directories;
    private final boolean autoReload;

    private FileSystemSkillRegistry(List<Path> directories, boolean autoReload) {
        super();
        this.directories = List.copyOf(directories);
        this.autoReload = autoReload;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected void loadSkills() throws SkillLoadingException {
        Map<String, SkillMetadata> newMetadata = new HashMap<>();

        for (Path dirPath : directories) {
            loadSkillsFromDirectory(dirPath, newMetadata);
        }

        metadataCache.putAll(newMetadata);
    }

    private void loadSkillsFromDirectory(Path dirPath, Map<String, SkillMetadata> metadataMap)
            throws SkillLoadingException {
        if (!Files.exists(dirPath)) {
            log.debug("Skills directory does not exist: {}", dirPath);
            return;
        }

        if (!Files.isDirectory(dirPath)) {
            log.warn("Skills path is not a directory: {}", dirPath);
            return;
        }

        try {
            try (var stream = Files.list(dirPath)) {
                stream.forEach(subDir -> {
                    if (!Files.isDirectory(subDir)) {
                        return;
                    }

                    Path skillFile = subDir.resolve(SKILL_MD_FILE);
                    if (!Files.exists(skillFile)) {
                        return;
                    }

                    String skillName = subDir.getFileName().toString();
                    try {
                        String content = Files.readString(skillFile);
                        SkillMetadata metadata = parseSkillMetadata(skillName, content, subDir, skillFile);
                        metadataMap.put(skillName, metadata);
                        log.debug("Loaded skill: {} from {}", skillName, dirPath);
                    } catch (IOException e) {
                        log.error("Failed to read skill file: {}", skillFile, e);
                    }
                });
            }
        } catch (IOException e) {
            throw new SkillLoadingException("Failed to load skills from directory: " + dirPath, e);
        }
    }

    private SkillMetadata parseSkillMetadata(String name, String content, Path skillPath, Path skillFile) {
        String description = extractDescription(name, content);
        List<String> allowedTools = extractAllowedTools(content);

        return SkillMetadata.builder()
                .name(name)
                .description(description)
                .skillPath(skillPath)
                .source(SkillMetadata.SkillSource.PROJECT)
                .allowedTools(allowedTools)
                .skillFile(skillFile)
                .build();
    }

    private String extractDescription(String name, String content) {
        String frontmatter = extractFrontmatter(content);
        if (frontmatter != null) {
            try {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(frontmatter);
                if (data != null && data.containsKey("description")) {
                    Object desc = data.get("description");
                    if (desc != null) {
                        return desc.toString();
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse frontmatter YAML for skill: {}", name, e);
            }
        }

        String withoutFrontmatter = removeFrontmatter(content);
        String[] lines = withoutFrontmatter.split("\n");
        StringBuilder desc = new StringBuilder();
        for (String line : lines) {
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                if (!desc.isEmpty()) break;
                continue;
            }
            if (desc.length() > 0) desc.append(" ");
            desc.append(line.trim());
        }
        return desc.length() > 0 ? desc.toString() : "Skill: " + name;
    }

    private List<String> extractAllowedTools(String content) {
        String frontmatter = extractFrontmatter(content);
        if (frontmatter == null) {
            return null;
        }

        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(frontmatter);
            if (data != null && data.containsKey("allowedTools")) {
                Object toolsObj = data.get("allowedTools");
                if (toolsObj instanceof List<?> toolsList) {
                    List<String> tools = new ArrayList<>();
                    for (Object item : toolsList) {
                        if (item != null) {
                            tools.add(item.toString());
                        }
                    }
                    return tools.isEmpty() ? null : tools;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse allowedTools from frontmatter", e);
        }

        return null;
    }

    private String extractFrontmatter(String content) {
        if (!content.startsWith("---")) {
            return null;
        }
        int endIndex = content.indexOf("---", 3);
        if (endIndex < 0) {
            return null;
        }
        return content.substring(3, endIndex);
    }

    private String removeFrontmatter(String content) {
        if (!content.startsWith("---")) {
            return content;
        }
        int endIndex = content.indexOf("---", 3);
        if (endIndex < 0) {
            return content;
        }
        return content.substring(endIndex + 3).trim();
    }

    @Override
    protected String loadContent(SkillMetadata metadata) throws SkillLoadingException {
        try {
            return Files.readString(metadata.skillFile());
        } catch (IOException e) {
            throw SkillLoadingException.ioException(metadata.name(), metadata.skillFile().toString(), e);
        }
    }

    @Override
    public void reload() throws SkillLoadingException {
        if (!autoReload) {
            throw new UnsupportedOperationException("Auto reload is not enabled");
        }
        clearCache();
        log.debug("Skills reloaded");
    }

    public static class Builder {
        private final List<Path> directories = new ArrayList<>();
        private boolean autoReload = false;

        public Builder addDirectory(String path) {
            return addDirectory(Path.of(path));
        }

        public Builder addDirectory(Path path) {
            directories.add(path);
            return this;
        }

        public Builder autoReload(boolean autoReload) {
            this.autoReload = autoReload;
            return this;
        }

        public FileSystemSkillRegistry build() {
            return new FileSystemSkillRegistry(directories, autoReload);
        }
    }
}
