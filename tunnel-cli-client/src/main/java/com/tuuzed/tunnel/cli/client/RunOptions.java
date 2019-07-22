package com.tuuzed.tunnel.cli.client;


import org.kohsuke.args4j.Option;

class RunOptions {

    @Option(name = "-c", aliases = {"--configFile"}, help = true, metaVar = "<string>", usage = "配置文件，当设置了配置文件时优先使用配置文件配置项")
    public String configFile = "";

    @Option(name = "-s", aliases = {"--serverAddr"}, help = true, metaVar = "<string>", usage = "服务器地址")
    public String serverAddr = "127.0.0.1";

    @Option(name = "-p", aliases = {"--serverPort"}, help = true, metaVar = "<int>", usage = "服务器端口")
    public int serverPort = 5000;

    @Option(name = "-la", aliases = {"--localAddr"}, help = true, metaVar = "<string>", usage = "内网地址")
    public String localAddr = "127.0.0.1";

    @Option(name = "-lp", aliases = {"--localPort"}, help = true, metaVar = "<int>", usage = "内网端口")
    public int localPort = 80;

    @Option(name = "-rp", aliases = {"--remotePort"}, help = true, metaVar = "<int>", usage = "映射外网端口")
    public int remotePort = 10080;

    @Option(name = "-t", aliases = {"--token"}, help = true, metaVar = "<int>", usage = "令牌")
    public String token = "";

    @Option(name = "-workerThreads", aliases = {"--workerThreads"}, help = true, metaVar = "<int>", usage = "Worker线程数量")
    public int workerThreads = -1;

    // SSL
    @Option(name = "-ssl", aliases = {"--ssl"}, help = true, metaVar = "<boolean>", usage = "是否启用SSL")
    public boolean ssl = false;

    @Option(name = "-ssl-jks", aliases = {"--ssl-jks"}, help = true, metaVar = "<string>", usage = "jks签名文件")
    public String sslJks = "";

    @Option(name = "-ssl-storepass", aliases = {"--ssl-storepass"}, help = true, metaVar = "<string>", usage = "jks签名文件Store密码")
    public String sslStorepass = "";


}