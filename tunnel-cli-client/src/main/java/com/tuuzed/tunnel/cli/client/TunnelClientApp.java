package com.tuuzed.tunnel.cli.client;

import com.esotericsoftware.yamlbeans.YamlReader;
import com.tuuzed.tunnel.cli.common.AbstractApp;
import com.tuuzed.tunnel.cli.common.CfgUtils;
import com.tuuzed.tunnel.client.TunnelClient;
import com.tuuzed.tunnel.common.logging.Logger;
import com.tuuzed.tunnel.common.logging.LoggerFactory;
import com.tuuzed.tunnel.common.proto.Proto;
import com.tuuzed.tunnel.common.proto.ProtoRequest;
import com.tuuzed.tunnel.common.util.SslContexts;
import io.netty.handler.ssl.SslContext;
import org.jetbrains.annotations.NotNull;

import java.io.FileReader;
import java.util.List;
import java.util.Map;

@SuppressWarnings("Duplicates")
public final class TunnelClientApp extends AbstractApp<RunOptions> {
    private static final Logger logger = LoggerFactory.getLogger(TunnelClientApp.class);

    @NotNull
    @Override
    public RunOptions newRunOptions() {
        return new RunOptions();
    }

    @Override
    public void runApp(@NotNull RunOptions runOptions) {
        try {
            if (runOptions.configFile.length() != 0) {
                runAppAtCfg(runOptions);
            } else {
                runAppAtArgs(runOptions);
            }
            System.in.read();
        } catch (Exception e) {
            logger.error("runApp Error", e);
        }
    }

    private void runAppAtCfg(@NotNull RunOptions runOptions) throws Exception {
        final YamlReader reader = new YamlReader(new FileReader(runOptions.configFile));
        final Map globalOptions = (Map) reader.read();

        // common
        final String serverAddr = CfgUtils.getString(globalOptions, "server_addr", "0.0.0.0");
        final int serverPort = CfgUtils.getInt(globalOptions, "server_addr", 5000);
        final String token = CfgUtils.getString(globalOptions, "token", "");
        final int workerThreads = CfgUtils.getInt(globalOptions, "worker_threads", -1);

        // ssl
        SslContext sslContext = null;
        int sslServerPort = serverPort;
        final Map sslOptions = CfgUtils.getMap(globalOptions, "ssl");
        if (!sslOptions.isEmpty()) {
            sslContext = SslContexts.forClient(
                CfgUtils.getString(sslOptions, "jks", ""),
                CfgUtils.getString(sslOptions, "storepass", "")
            );
            sslServerPort = CfgUtils.getInt(sslOptions, "server_port", 5001);
        }

        final TunnelClient tunnelClient = TunnelClient.builder()
            .setWorkerThreads(workerThreads)
            .setAutoReconnect(true)
            .build();

        final List<Map> tunnels = CfgUtils.getListMap(globalOptions, "tunnels");

        for (Map tunnel : tunnels) {
            final Proto proto = Proto.valueOf(
                CfgUtils.getString(tunnel, "proto", "tcp").toUpperCase()
            );
            final boolean enableSsl = CfgUtils.getBoolean(tunnel, "enable_ssl", false);
            final String localAddr = CfgUtils.getString(tunnel, "local_addr", "");
            final int localPort = CfgUtils.getInt(tunnel, "local_port", 0);

            ProtoRequest protoRequest = null;
            switch (proto) {
                case TCP:
                    final int remotePort = CfgUtils.getInt(tunnel, "remote_port", 0);
                    protoRequest = ProtoRequest.tcpBuilder(remotePort)
                        .setLocalAddr(localAddr)
                        .setLocalPort(localPort)
                        .setOption("token", token)
                        .build();
                    break;
                case HTTP:
                    final String vhost = CfgUtils.getString(tunnel, "vhost", "");

                    StringBuilder setHeaders = new StringBuilder();
                    StringBuilder addHeaders = new StringBuilder();

                    boolean isFirst = true;

                    for (Object header : CfgUtils.getMap(tunnel, "set_headers").entrySet()) {
                        if (header instanceof Map.Entry) {
                            String key = ((Map.Entry) header).getKey().toString();
                            String value = ((Map.Entry) header).getValue().toString();
                            setHeaders.append(key).append(":").append(value);
                            if (!isFirst) {
                                setHeaders.append(";");
                            }
                            isFirst = false;
                        }
                    }

                    for (Object header : CfgUtils.getMap(tunnel, "add_headers").entrySet()) {
                        if (header instanceof Map.Entry) {
                            String key = ((Map.Entry) header).getKey().toString();
                            String value = ((Map.Entry) header).getValue().toString();
                            addHeaders.append(key).append(":").append(value);
                            if (!isFirst) {
                                addHeaders.append(";");
                            }
                            isFirst = false;
                        }
                    }

                    protoRequest = ProtoRequest.httpBuilder(vhost)
                        .setLocalAddr(localAddr)
                        .setLocalPort(localPort)
                        .setOption("token", token)
                        .setOption("set_headers", setHeaders.toString())
                        .setOption("add_headers", addHeaders.toString())
                        .build();
                    break;
                default:
                    break;
            }
            if (protoRequest != null) {
                tunnelClient.connect(
                    serverAddr,
                    (enableSsl) ? sslServerPort : serverPort,
                    protoRequest,
                    (enableSsl) ? sslContext : null
                );
            }
        }
    }

    private void runAppAtArgs(@NotNull RunOptions runOptions) throws Exception {
        TunnelClient tunnelClient = TunnelClient.builder()
            .setWorkerThreads(runOptions.workerThreads)
            .setAutoReconnect(true)
            .build();

        Proto proto = Proto.valueOf(runOptions.proto.toUpperCase());

        ProtoRequest protoRequest = null;
        switch (proto) {
            case TCP:
                protoRequest = ProtoRequest.tcpBuilder(runOptions.remotePort)
                    .setLocalAddr(runOptions.localAddr)
                    .setLocalPort(runOptions.localPort)
                    .setOption("token", runOptions.token)
                    .build();

                break;
            case HTTP:
                protoRequest = ProtoRequest.httpBuilder(runOptions.vhost)
                    .setLocalAddr(runOptions.localAddr)
                    .setLocalPort(runOptions.localPort)
                    .setOption("token", runOptions.token)
                    .build();
                break;
            default:
                break;
        }
        SslContext sslContext = null;
        if (runOptions.ssl) {
            sslContext = SslContexts.forClient(runOptions.sslJks, runOptions.sslStorepass);
        }
        if (protoRequest != null) {
            tunnelClient.connect(runOptions.serverAddr, runOptions.serverPort, protoRequest, sslContext);
        }
    }


}
