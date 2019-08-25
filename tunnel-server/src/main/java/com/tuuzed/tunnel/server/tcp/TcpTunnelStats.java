package com.tuuzed.tunnel.server.tcp;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TcpTunnelStats {
    private static final Logger logger = LoggerFactory.getLogger(TcpTunnelStats.class);

    @NotNull
    private final Map<Integer, Item> items = new ConcurrentHashMap<>();

    @NotNull
    public Item getItem(int port) {
        Item item = items.get(port);
        if (item == null) {
            item = new Item(port);
            items.put(port, item);
        }
        return item;
    }

    @NotNull
    public List<Item> getItems() {
        return new ArrayList<>(items.values());
    }

    public void removeAll() {
        items.clear();
    }

    public void remove(int port) {
        items.remove(port);
    }

    public static class Item {
        private final int port;
        private AtomicLong readBytes = new AtomicLong(0);
        private AtomicLong writeBytes = new AtomicLong(0);
        private AtomicLong readMsgs = new AtomicLong(0);
        private AtomicLong writeMsgs = new AtomicLong(0);
        private AtomicInteger channels = new AtomicInteger(0);
        private long timestamp = 0;

        private Item(int port) {
            this.port = port;
        }

        public void incrementReadBytes(int count) {
            readBytes.addAndGet(count);
            timestamp = System.currentTimeMillis();
            logger.trace("TcpTunnelStats: {}", this);
        }

        public void incrementWriteBytes(int count) {
            writeBytes.addAndGet(count);
            timestamp = System.currentTimeMillis();
            logger.trace("TcpTunnelStats: {}", this);
        }

        public void incrementReadMsgs(int count) {
            readMsgs.addAndGet(count);
            timestamp = System.currentTimeMillis();
            logger.trace("TcpTunnelStats: {}", this);
        }

        public void incrementWriteMsgs(int count) {
            writeMsgs.addAndGet(count);
            timestamp = System.currentTimeMillis();
            logger.trace("TcpTunnelStats: {}", this);
        }

        public void incrementChannels() {
            channels.incrementAndGet();
            timestamp = System.currentTimeMillis();
            logger.trace("TcpTunnelStats: {}", this);
        }

        public void decrementChannels() {
            timestamp = System.currentTimeMillis();
            channels.decrementAndGet();
            logger.trace("TcpTunnelStats: {}", this);
        }

        public void resetReadStat() {
            readBytes.set(0);
            readMsgs.set(0);
        }

        public void resetWriteStat() {
            readMsgs.set(0);
            writeMsgs.set(0);
        }

        public int getPort() {
            return port;
        }

        public long getReadBytes() {
            return readBytes.get();
        }

        public long getWriteBytes() {
            return writeBytes.get();
        }

        public long getReadMsgs() {
            return readMsgs.get();
        }

        public long getWriteMsgs() {
            return writeMsgs.get();
        }

        public int getChannels() {
            return channels.get();
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "{" +
                "port=" + port +
                ", readBytes=" + readBytes +
                ", writeBytes=" + writeBytes +
                ", readMsgs=" + readMsgs +
                ", writeMsgs=" + writeMsgs +
                ", channels=" + channels +
                ", timestamp=" + timestamp +
                '}';
        }
    }
}
