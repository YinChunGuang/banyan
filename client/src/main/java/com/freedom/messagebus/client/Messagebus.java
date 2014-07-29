package com.freedom.messagebus.client;

import com.freedom.messagebus.client.core.config.ConfigManager;
import com.freedom.messagebus.client.core.config.IConfigChangedListener;
import com.freedom.messagebus.client.core.config.LongLiveZookeeper;
import com.freedom.messagebus.client.core.pool.AbstractPool;
import com.freedom.messagebus.client.core.pool.ChannelFactory;
import com.freedom.messagebus.client.core.pool.ChannelPool;
import com.freedom.messagebus.client.core.pool.ChannelPoolConfig;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Properties;

/**
 * the main operator of messagebus client
 */
public class Messagebus {

    private static final Log logger = LogFactory.getLog(Messagebus.class);

    private static volatile Messagebus instance = null;

    @NotNull
    private IProducer producer;
    @NotNull
    private IConsumer consumer;

    @NotNull
    private ZooKeeper             zookeeper;
    @NotNull
    private ConfigManager         configManager;
    private AbstractPool<Channel> pool;
    @NotNull
    private Connection            connection;

    private boolean isOpen         = false;
    private boolean useChannelPool = false;

    @NotNull
    private String zkHost;
    private int    zkPort;

    private Messagebus() {

    }

    public static Messagebus getInstance() {
        if (instance == null) {
            synchronized (Messagebus.class) {
                if (instance == null) {
                    instance = new Messagebus();
                }
            }
        }

        return instance;
    }

    /**
     * this method used to do some init thing before carrying message
     * it will create some expensive big object
     * so do NOT invoke it frequently
     *
     * @throws MessagebusConnectedFailedException
     */
    public void open() throws MessagebusConnectedFailedException {
        //load class
        this.zookeeper = LongLiveZookeeper.getZKInstance(this.getZkHost(), this.getZkPort());
        this.configManager = ConfigManager.getInstance(this.zookeeper);
        int pathNum = this.configManager.getPaths().size();
        LongLiveZookeeper.watchPaths(configManager.getPaths().toArray(new String[pathNum]),
                                     new IConfigChangedListener() {
                                         @Override
                                         public void onChanged(String path, byte[] newData, Watcher.Event.EventType eventType) {
                                             logger.debug("path : " + path + " has changed!");
                                         }
                                     });

        this.initConnection();

        this.useChannelPool = Boolean.valueOf(configManager.getConfigProperty().getProperty("messagebus.client.useChannelPool"));
        //if use channel pool
        if (this.useChannelPool) {
            this.initChannelPool();
        }

        GenericContext context = new GenericContext();
        context.setPool(this.pool);
        context.setConfigManager(this.configManager);
        context.setZooKeeper(this.zookeeper);
        context.setConnection(this.connection);

        producer = new GenericProducer(context);
        consumer = new GenericConsumer(context);

        isOpen = true;
    }

    /**
     * close the messagebus client and release all used resources
     * pls invoke this method after making sure you will not use the client in
     * current context.
     */
    public void close() {
        //release all resource
        try {
            this.configManager.destroy();

            if (this.useChannelPool)
                pool.destroy();

            if (this.connection.isOpen())
                this.connection.close();

            LongLiveZookeeper.close();

            this.isOpen = false;
        } catch (IOException e) {
            logger.error("[close] occurs a IOException : " + e.getMessage());
        }
    }

    @NotNull
    public IProducer getProducer() throws MessagebusUnOpenException {
        if (!this.isOpen())
            throw new MessagebusUnOpenException
                ("Illegal State: please call Messagebus#open() first!");

        return producer;
    }

    @NotNull
    public IConsumer getConsumer() throws MessagebusUnOpenException {
        if (!this.isOpen())
            throw new MessagebusUnOpenException
                ("Illegal State: please call Messagebus#open() first!");

        return consumer;
    }

    public boolean isOpen() {
        return this.isOpen;
    }

    @NotNull
    public String getZkHost() {
        if (this.zkHost == null || this.zkHost.isEmpty())
            this.zkHost = "localhost";

        return zkHost;
    }

    public void setZkHost(@NotNull String zkHost) {
        this.zkHost = zkHost;
    }

    public int getZkPort() {
        if (this.zkPort == 0)
            this.zkPort = 2181;

        return zkPort;
    }

    public void setZkPort(int zkPort) {
        this.zkPort = zkPort;
    }

    private void initConnection() throws MessagebusConnectedFailedException {
        try {
            String host = this.configManager.getConfigProperty().getProperty("messagebus.client.host");

            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setHost(host);

            this.connection = connectionFactory.newConnection();
        } catch (IOException e) {
            throw new MessagebusConnectedFailedException(e);
        }
    }

    private void initChannelPool() {
        Properties poolConfig = this.configManager.getPoolProperties();

        ChannelPoolConfig config = new ChannelPoolConfig();
        config.setMaxTotal(Integer.valueOf(poolConfig.getProperty("channel.pool.maxTotal")));
        config.setMaxIdle(Integer.valueOf(poolConfig.getProperty("channel.pool.maxIdle")));
        config.setMaxWaitMillis(Long.valueOf(poolConfig.getProperty("channel.pool.maxWait")));
        config.setTestOnBorrow(Boolean.valueOf(poolConfig.getProperty("channel.pool.testOnBorrow")));
        config.setTestOnReturn(Boolean.valueOf(poolConfig.getProperty("channel.pool.testOnReturn")));

        pool = new ChannelPool(config, new ChannelFactory(this.connection));
    }

}