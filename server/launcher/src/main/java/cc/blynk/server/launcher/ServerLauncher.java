package cc.blynk.server.launcher;

import cc.blynk.server.Holder;
import cc.blynk.server.api.http.AppAndHttpsServer;
import cc.blynk.server.api.http.HardwareAndHttpAPIServer;
import cc.blynk.server.application.AppServer;
import cc.blynk.server.core.BaseServer;
import cc.blynk.server.hardware.HardwareSSLServer;
import cc.blynk.server.hardware.HardwareServer;
import cc.blynk.server.hardware.MQTTHardwareServer;
import cc.blynk.utils.AppNameUtil;
import cc.blynk.utils.JarUtil;
import cc.blynk.utils.LoggerUtil;
import cc.blynk.utils.SHA256Util;
import cc.blynk.utils.properties.GCMProperties;
import cc.blynk.utils.properties.MailProperties;
import cc.blynk.utils.properties.ServerProperties;
import cc.blynk.utils.properties.SmsProperties;
import cc.blynk.utils.properties.TwitterProperties;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.File;
import java.net.BindException;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;

/**
 * Entry point for server launch.
 *
 * By default starts 7 servers on different ports:
 *
 * 1 server socket for SSL/TLS Hardware (8441 default)
 * 1 server socket for plain tcp/ip Hardware (8442 default)
 * 1 server socket for SSL/TLS Applications (8443 default)
 * 1 server socket for HTTP API (8080 default)
 * 1 server socket for HTTPS API (9443 default)
 * 1 server socket for MQTT (8440 default)
 * 1 server socket for Administration UI (7443 default)
 *
 * In addition launcher start all related to business logic threads like saving user profiles thread, timers
 * processing thread, properties reload thread and shutdown hook tread.
 *
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/16/2015.
 */
public final class ServerLauncher {

    //required for QR generation
    static {
        System.setProperty("java.awt.headless", "true");
    }

    private ServerLauncher() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> cmdProperties = ArgumentsParser.parse(args);

        ServerProperties serverProperties = new ServerProperties(cmdProperties);

        LoggerUtil.configureLogging(serverProperties);

        //required for logging dynamic context
        System.setProperty("data.folder", serverProperties.getProperty("data.folder"));

        //required to avoid dependencies within model to server.properties
        setGlobalProperties(serverProperties);

        MailProperties mailProperties = new MailProperties(cmdProperties);
        SmsProperties smsProperties = new SmsProperties(cmdProperties);
        GCMProperties gcmProperties = new GCMProperties(cmdProperties);
        TwitterProperties twitterProperties = new TwitterProperties(cmdProperties);

        Security.addProvider(new BouncyCastleProvider());

        boolean restore = Boolean.parseBoolean(cmdProperties.get(ArgumentsParser.RESTORE_OPTION));
        start(serverProperties, mailProperties, smsProperties, gcmProperties, twitterProperties, restore);
    }

    private static void setGlobalProperties(ServerProperties serverProperties) {
        Map<String, String> globalProps = new HashMap<>(4);
        globalProps.put("terminal.strings.pool.size", "25");
        globalProps.put("initial.energy", "2000");
        globalProps.put("table.rows.pool.size", "100");
        globalProps.put("csv.export.data.points.max", "43200");

        for (Map.Entry<String, String> entry : globalProps.entrySet()) {
            String name = entry.getKey();
            String value = serverProperties.getProperty(name, entry.getValue());
            System.setProperty(name, value);
        }
    }

    private static void start(ServerProperties serverProperties, MailProperties mailProperties,
                              SmsProperties smsProperties, GCMProperties gcmProperties,
                              TwitterProperties twitterProperties,
                              boolean restore) {
        Holder holder = new Holder(serverProperties,
                mailProperties, smsProperties, gcmProperties, twitterProperties,
                restore);

        BaseServer[] servers = new BaseServer[] {
                new HardwareServer(holder),
                new HardwareSSLServer(holder),
                new AppServer(holder),
                new HardwareAndHttpAPIServer(holder),
                new AppAndHttpsServer(holder),
                new MQTTHardwareServer(holder)
        };

        if (startServers(servers)) {
            //Launching all background jobs.
            JobLauncher.start(holder, servers);

            System.out.println();
            System.out.println("Blynk Server " + JarUtil.getServerVersion() + " successfully started.");
            String path = new File(System.getProperty("logs.folder")).getAbsolutePath().replace("/./", "/");
            System.out.println("All server output is stored in folder '" + path + "' file.");

            holder.sslContextHolder.generateInitialCertificates(holder.props);

            createSuperUser(holder);
        }
    }

    private static void createSuperUser(Holder holder) {
        String email = holder.props.getProperty("admin.email", "admin@blynk.cc");
        String pass = holder.props.getProperty("admin.pass", "admin");

        if (!holder.userDao.isSuperAdminExists()) {
            System.out.println("Your Admin login email is " + email);
            System.out.println("Your Admin password is " + pass);

            String hash = SHA256Util.makeHash(pass, email);
            holder.userDao.add(email, hash, AppNameUtil.BLYNK, true);
        }
    }

    private static boolean startServers(BaseServer[] servers) {
        //start servers
        try {
            for (BaseServer server : servers) {
                server.start();
            }
            return true;
        } catch (BindException bindException) {
            System.out.println("Server ports are busy. Most probably server already launched. See "
                    + new File(System.getProperty("logs.folder")).getAbsolutePath() + " for more info.");
        } catch (Exception e) {
            System.out.println("Error starting Blynk server. Stopping.");
        }

        return false;
    }

}
