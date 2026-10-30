package io.github.quan0505.daednext;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Only the fixed loopback service receives credentials. No proxy or redirects. */
final class ApiClient {
    private final ModuleClient module;
    private String token;
    private long expiresAt;
    ApiClient(ModuleClient module) { this.module = module; }
    synchronized void clear() { token = null; expiresAt = 0; }
    String panelSession() throws Exception { return session(); }
    private synchronized String session() throws Exception {
        if (token != null && expiresAt > System.currentTimeMillis() / 1000 + 30) return token;
        JSONObject response = new JSONObject(module.command("manager-session"));
        if (response.optBoolean("setupRequired")) throw new IOException(L10n.tr("请先在高级面板创建管理账户"));
        if (!response.optBoolean("ok")) throw new IOException(L10n.tr("无法建立模块会话，请检查模块版本与运行状态"));
        token = response.getString("token"); expiresAt = response.getLong("expiresAt");
        return token;
    }
    JSONObject get(String path) throws Exception { return request("GET", path, null, true); }
    JSONObject post(String path, JSONObject body) throws Exception { return request("POST", path, body, false); }
    JSONObject patch(String path, JSONObject body) throws Exception { return request("PUT", path, body, false); }
    JSONObject delete(String path) throws Exception { return request("DELETE", path, null, false); }
    JSONObject delete(String path, JSONObject body) throws Exception { return request("DELETE", path, body, false); }
    private JSONObject request(String method, String path, JSONObject body, boolean retry) throws Exception {
        if (!path.matches("[a-zA-Z0-9/_?=&.-]+") || path.startsWith("/")) throw new IOException(L10n.tr("无效的接口路径"));
        HttpURLConnection connection = (HttpURLConnection)new URL("http://127.0.0.1:2023/api/" + path).openConnection(Proxy.NO_PROXY);
        connection.setConnectTimeout(6000); connection.setReadTimeout(90000);
        connection.setInstanceFollowRedirects(false); connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        try {
            connection.setRequestProperty("Authorization", "Bearer " + session());
            if (body != null) {
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int code = connection.getResponseCode();
            if (code == 401) {
                clear();
                if (retry && method.equals("GET")) return request(method, path, body, false);
                throw new IOException(L10n.tr("会话已失效，请刷新后重试；本次修改未自动重发"));
            }
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] bytes = new byte[8192]; int n;
            if (stream != null) try (InputStream in = stream) {
                while ((n = in.read(bytes)) != -1) {
                    if (out.size() + n > 4 * 1024 * 1024) throw new IOException(L10n.tr("响应内容过大"));
                    out.write(bytes, 0, n);
                }
            }
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
            JSONObject json = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            if (code < 200 || code >= 300) throw new IOException(L10n.tr("请求失败（") + code + L10n.tr("）：") + json.optString("error", L10n.tr("请检查服务状态")));
            return json;
        } finally { connection.disconnect(); }
    }
}
