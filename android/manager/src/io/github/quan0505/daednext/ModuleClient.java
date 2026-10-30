package io.github.quan0505.daednext;

import android.util.Base64;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Root is requested from the user's root manager. No permission database edits. */
final class ModuleClient {
    static final String CTL = "/data/adb/modules/daenext/bin/daenextctl";
    private static final Set<String> COMMANDS = new HashSet<>(Arrays.asList(
        "status", "start", "stop", "restart", "backup", "logs", "diagnose", "manager-session",
        "wifi-policy get", "autostart on", "autostart off", "google-network on", "google-network off"));
    private static final ExecutorService READERS = Executors.newCachedThreadPool();

    String command(String action) throws Exception {
        if (!COMMANDS.contains(action) && !action.matches("wifi-policy set [A-Za-z0-9+/=]{1,24000}"))
            throw new IOException("不支持的模块操作");
        Process process;
        try { process = new ProcessBuilder("su", "-c", CTL + " " + action).start(); }
        catch (IOException error) { throw new IOException("无法访问 su，请在当前 Root 管理器中授予 DaedNext Root 权限，然后重新打开 App"); }
        Future<String> out = READERS.submit(() -> read(process.getInputStream()));
        Future<String> err = READERS.submit(() -> read(process.getErrorStream()));
        try {
            if (!process.waitFor(65, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("操作等待超时，请检查模块状态后再试；不要重复提交");
            }
            String stdout = out.get(3, TimeUnit.SECONDS).trim();
            String stderr = err.get(3, TimeUnit.SECONDS).trim();
            if (process.exitValue() != 0) {
                if (stderr.contains("Permission denied") || stderr.contains("not allowed") || stderr.contains("denied"))
                    throw new IOException("需要 Root 授权：请在当前 Root 管理器中允许 DaedNext 使用超级用户权限");
                if (stderr.contains("not found") || stderr.contains("No such file"))
                    throw new IOException("未找到兼容模块，请先安装 DaedNext alpha7 或更新版本");
                throw new IOException(stderr.isEmpty() ? "模块操作失败，请检查 Root 授权与模块安装状态" : stderr);
            }
            return stdout;
        } finally {
            process.getInputStream().close(); process.getErrorStream().close(); process.getOutputStream().close();
        }
    }

    JSONObject status() throws Exception { return new JSONObject(command("status")); }
    JSONObject wifi() throws Exception { return new JSONObject(command("wifi-policy get")); }
    void saveWifi(JSONObject config) throws Exception {
        String data = Base64.encodeToString(config.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        command("wifi-policy set " + data);
    }
    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        while ((n = input.read(buffer)) != -1) {
            if (out.size() + n > 2 * 1024 * 1024) throw new IOException("模块响应过大");
            out.write(buffer, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
