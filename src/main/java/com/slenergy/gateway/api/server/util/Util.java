package com.slenergy.gateway.api.server.util;

import com.slenergy.gateway.database.sqlite.SQLiteConnection;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.Map;

/**
 * class Util description
 *
 * @author Eric Li
 * @version 1.0
 *
 * @since 2024-04-22
 * @since 1.0
 */
public final class Util {

    private static String replaceIPaddressSubnet(String ip, int subnet) {
        StringBuilder sb = new StringBuilder(ip);
        sb.replace(ip.lastIndexOf(".") + 1, ip.length(), String.valueOf(subnet));
        return sb.toString();
    }

    public static String getLocalIp() throws UnknownHostException {
        InetAddress currentIp = Inet4Address.getLocalHost();
        return currentIp.getHostAddress();
    }

    public static Pair<String, Integer> generateIPaddress(SQLiteConnection connection) throws UnknownHostException, SQLException {
        String ipStr = getLocalIp();
        Map<String, Object> addrInfo = connection.queryOne("select subnet from address order by subnet desc limit 1;", null);
        int subnet = 0;
        if (addrInfo != null)
            subnet = (Integer) addrInfo.get("subnet");
        else
            subnet = Integer.parseInt(ipStr.substring(ipStr.lastIndexOf(".") + 1));
        subnet += 1;

        return Pair.create(replaceIPaddressSubnet(ipStr, subnet), subnet);
    }

}
