package net.iponweb.disthene.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Andrei Ivanov
 */
@ConfigurationProperties(prefix = "graphene.writer.index")
public class IndexConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(IndexConfiguration.class);

    private String name;
    private String index;
    private String type;
    private boolean cache;
    private long expire;
    private List<String> cluster = new ArrayList<>();
    private int port;
    private IndexBulkConfiguration bulk;

    @PostConstruct
    public void init() {
        logger.info("Load Graphene index configuration : {}", toString());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public List<String> getCluster() {
        return cluster;
    }

    public void setCluster(List<String> cluster) {
        this.cluster = cluster;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public IndexBulkConfiguration getBulk() {
        return bulk;
    }

    public void setBulk(IndexBulkConfiguration bulk) {
        this.bulk = bulk;
    }

    public boolean isCache() {
        return cache;
    }

    public void setCache(boolean cache) {
        this.cache = cache;
    }

    public long getExpire() {
        return expire;
    }

    public void setExpire(long expire) {
        this.expire = expire;
    }

    @Override
    public String toString() {
        return "IndexConfiguration{" +
                "name='" + name + '\'' +
                ", index='" + index + '\'' +
                ", type='" + type + '\'' +
                ", cache=" + cache +
                ", expire=" + expire +
                ", cluster=" + cluster +
                ", port=" + port +
                ", bulk=" + bulk +
                '}';
    }
}
