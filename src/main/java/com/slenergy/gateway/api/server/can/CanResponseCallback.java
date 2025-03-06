package com.slenergy.gateway.api.server.can;

import com.slenergy.gateway.database.sqlite.SQLiteConnection;

import java.sql.SQLException;

/**
 * can设备消息发送回调接口
 */
public interface CanResponseCallback {
    void onResponse(String response) throws SQLException;
}
