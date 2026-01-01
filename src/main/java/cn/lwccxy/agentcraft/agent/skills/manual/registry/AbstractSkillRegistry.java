package cn.lwccxy.agentcraft.agent.skills.manual.registry;

import cn.lwccxy.agentcraft.agent.skills.manual.model.SkillLoadingException;
import cn.lwccxy.agentcraft.agent.skills.manual.model.SkillMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能注册表抽象基类。
 *
 * 提供技能元数据和内容的缓存功能，子类只需实现具体的加载逻辑。
 *
 * @author rel
 * 
 */
public abstract class AbstractSkillRegistry implements SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(AbstractSkillRegistry.class);

    protected final Map<String, SkillMetadata> metadataCache = new ConcurrentHashMap<>();
    protected final Map<String, String> contentCache = new ConcurrentHashMap<>();
    protected volatile boolean loaded = false;

    private final boolean cacheEnabled;

    protected AbstractSkillRegistry() {
        this(true);
    }

    protected AbstractSkillRegistry(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    @Override
    public List<SkillMetadata> listAll() throws SkillLoadingException {
        ensureLoaded();
        return List.copyOf(metadataCache.values());
    }

    @Override
    public SkillMetadata get(String name) throws SkillLoadingException {
        ensureLoaded();
        return metadataCache.get(name);
    }

    @Override
    public boolean contains(String name) {
        try {
            ensureLoaded();
        } catch (SkillLoadingException e) {
            log.error("Failed to load skills", e);
            return false;
        }
        return metadataCache.containsKey(name);
    }

    @Override
    public int size() {
        try {
            ensureLoaded();
        } catch (SkillLoadingException e) {
            log.error("Failed to load skills", e);
            return 0;
        }
        return metadataCache.size();
    }

    @Override
    public String readSkillContent(String name) throws SkillLoadingException {
        if (cacheEnabled && contentCache.containsKey(name)) {
            return contentCache.get(name);
        }

        ensureLoaded();

        SkillMetadata metadata = metadataCache.get(name);
        if (metadata == null) {
            throw SkillLoadingException.notFound(name);
        }

        String content = loadContent(metadata);

        if (cacheEnabled) {
            contentCache.put(name, content);
        }

        return content;
    }

    @Override
    public void clearCache() {
        metadataCache.clear();
        contentCache.clear();
        loaded = false;
        log.debug("Skill registry cache cleared");
    }

    protected void ensureLoaded() throws SkillLoadingException {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    loadSkills();
                    loaded = true;
                    log.debug("Skills loaded: {} skills", metadataCache.size());
                }
            }
        }
    }

    protected abstract void loadSkills() throws SkillLoadingException;

    protected abstract String loadContent(SkillMetadata metadata) throws SkillLoadingException;

    protected boolean isCacheEnabled() {
        return cacheEnabled;
    }
}
