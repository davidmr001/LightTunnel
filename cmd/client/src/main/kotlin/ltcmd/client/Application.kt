package ltcmd.client

import io.netty.handler.ssl.SslContext
import lighttunnel.client.TunnelClient
import lighttunnel.client.callback.OnTunnelStateListener
import lighttunnel.client.connect.TunnelConnectDescriptor
import lighttunnel.cmd.AbstractApplication
import lighttunnel.cmd.IpAddressUtil
import lighttunnel.cmd.base.BuildConfig
import lighttunnel.logger.LoggerFactory
import lighttunnel.logger.loggerDelegate
import lighttunnel.proto.TunnelRequest
import lighttunnel.util.SslContextUtil
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Options
import org.apache.log4j.Level
import org.apache.log4j.helpers.OptionConverter
import org.ini4j.Ini
import org.ini4j.Profile
import java.io.File

class Application : AbstractApplication(), OnTunnelStateListener {
    private val logger by loggerDelegate()

    override fun onConnecting(descriptor: TunnelConnectDescriptor, reconnect: Boolean) {
        logger.info("onConnecting: {}, reconnect: {}", descriptor, reconnect)
    }

    override fun onConnected(descriptor: TunnelConnectDescriptor) {
        logger.info("onConnected: {}", descriptor)
    }

    override fun onDisconnect(descriptor: TunnelConnectDescriptor, err: Boolean, errCause: Throwable?) {
        logger.info("onDisconnect: {}, err: {}", descriptor, err, errCause)
    }

    override val options: Options
        get() = Options().apply {
            addOption("h", "help", false, "帮助信息")
            addOption("v", "version", false, "版本信息")
            addOption("c", "config", true, "配置文件, 默认为ltc.ini")
        }

    override fun main(commandLine: CommandLine) {
        if (commandLine.hasOption("h")) {
            printUsage()
            return
        }
        if (commandLine.hasOption("v")) {
            System.out.printf("%s%n", BuildConfig.VERSION_NAME)
            return
        }
        val configFilePath = commandLine.getOptionValue("c") ?: "ltc.ini"
        val ini = Ini()
        ini.load(File(configFilePath))
        val basic = ini["basic"] ?: return
        setupLogger(basic)
        val client = newTunnelClient(basic)
        val serverAddr = basic["server_addr"] ?: "127.0.0.1"
        val serverPort = basic["server_port"].asInt() ?: 5080
        val sslContext = newSslContext(basic)
        val sslServerPort = basic["ssl_server_port"].asInt() ?: 5443
        ini.entries.filter { it.key != "basic" }.map {
            Pair(it.value["ssl"]?.toUpperCase() == "TRUE", newTunnelRequest(basic, it.value))
        }.forEach {
            val ssl = it.first
            val request = it.second
            if (request != null) {
                if (ssl) {
                    client.connect(serverAddr, sslServerPort, request, sslContext)
                } else {
                    client.connect(serverAddr, serverPort, request, null)
                }
            }
        }
    }

    private fun newSslContext(basic: Profile.Section): SslContext {
        val jks = basic["ssl_jks"] ?: return SslContextUtil.forBuiltinClient()
        val storePassword = basic["ssl_store_password"] ?: return SslContextUtil.forBuiltinClient()
        return try {
            SslContextUtil.forClient(jks, storePassword)
        } catch (e: Exception) {
            logger.warn("tunnel ssl used builtin jks.")
            SslContextUtil.forBuiltinServer()
        }
    }

    private fun newTunnelClient(basic: Profile.Section): TunnelClient {
        val workerThreads = basic["worker_threads"].asInt() ?: -1
        return TunnelClient(
            workerThreads = workerThreads,
            loseReconnect = true,
            errorReconnect = false,
            onTunnelStateListener = this,
            // dashboard
            dashboardBindPort = basic["dashboard_bind_port"].asInt()
        )
    }

    private fun newTunnelRequest(basic: Profile.Section, tunnel: Profile.Section): TunnelRequest? {
        val type = tunnel["type"] ?: "tcp"
        return when (type.toUpperCase()) {
            "TCP" -> newTcpTunnelRequest(basic, tunnel)
            "HTTP" -> newHttpTunnelRequest(basic, tunnel)
            "HTTPS" -> newHttpsTunnelRequest(basic, tunnel)
            else -> null
        }
    }

    private fun newTcpTunnelRequest(basic: Profile.Section, tunnel: Profile.Section): TunnelRequest? {
        val authToken = basic["auth_token"]
        val localAddr = tunnel["local_addr"] ?: IpAddressUtil.localIpV4 ?: "127.0.0.1"
        val localPort = tunnel["local_port"].asInt() ?: 80
        val remotePort = tunnel["remote_port"].asInt() ?: 0
        return TunnelRequest.forTcp(
            localAddr = localAddr,
            localPort = localPort,
            remotePort = remotePort,
            name = tunnel.name,
            authToken = authToken
        )
    }

    private fun newHttpTunnelRequest(basic: Profile.Section, tunnel: Profile.Section): TunnelRequest? {
        return newHttpOrHttpsTunnelRequest(basic, tunnel, false)
    }

    private fun newHttpsTunnelRequest(basic: Profile.Section, tunnel: Profile.Section): TunnelRequest? {
        return newHttpOrHttpsTunnelRequest(basic, tunnel, true)
    }

    private fun newHttpOrHttpsTunnelRequest(basic: Profile.Section, tunnel: Profile.Section, https: Boolean): TunnelRequest? {
        val authToken = basic["auth_token"]
        val localAddr = tunnel["local_addr"] ?: IpAddressUtil.localIpV4 ?: "127.0.0.1"
        val localPort = tunnel["local_port"].asInt() ?: 80
        val customDomain = tunnel["custom_domain"] ?: return null
        val proxySetHeaders = mapOf(
            *tunnel.entries
                .filter { it.key.startsWith("pxy_header_set_") && it.value.isNotEmpty() }
                .map { Pair(it.key.substring("pxy_header_set_".length), it.value.toString()) }.toTypedArray()
        )
        val proxyAddHeaders = mapOf(
            *tunnel.entries
                .filter { it.key.startsWith("pxy_header_add_") && it.value.isNotEmpty() }
                .map { Pair(it.key.substring("pxy_header_add_".length), it.value.toString()) }.toTypedArray()
        )
        val enableBasicAuth = tunnel["auth_enable"]?.toUpperCase() == "TRUE"
        val basicAuthRealm = tunnel["auth_realm"] ?: "."
        val basicAuthUsername = tunnel["auth_username"] ?: "guest"
        val basicAuthPassword = tunnel["auth_password"] ?: "guest"
        return TunnelRequest.forHttp(
            localAddr = localAddr,
            localPort = localPort,
            https = https,
            name = tunnel.name,
            authToken = authToken,
            host = customDomain,
            pxySetHeaders = proxySetHeaders,
            pxyAddHeaders = proxyAddHeaders,
            enableBasicAuth = enableBasicAuth,
            basicAuthRealm = basicAuthRealm,
            basicAuthUsername = basicAuthUsername,
            basicAuthPassword = basicAuthPassword
        )
    }

    private fun setupLogger(basic: Profile.Section) {
        val logLevel = Level.toLevel(basic["log_level"], Level.INFO)
        val logFile = basic["log_file"]
        val logCount = basic["log_count"].asInt() ?: 3
        val logSize = basic["log_size"] ?: "1MB"
        LoggerFactory.configConsole(Level.OFF, names = *arrayOf(
            "io.netty",
            "org.ini4j",
            "org.slf4j",
            "org.json",
            "org.apache.commons.cli"
        ))
        LoggerFactory.configConsole(level = logLevel)
        if (logFile != null) {
            LoggerFactory.configFile(
                level = logLevel,
                file = logFile,
                maxBackupIndex = logCount,
                maxFileSize = OptionConverter.toFileSize(logSize, 1)
            )
        }
    }

    private fun String?.asInt(): Int? {
        return try {
            this?.toInt()
        } catch (e: NumberFormatException) {
            return null
        }
    }

}