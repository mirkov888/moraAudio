// Mora Audio Suite - made by Mora & Claude Code
package app.mora.audio;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Runs shell commands as root (KernelSU / Magisk / APatch grant it on first use). */
final class Root {
    /** Finds the Mora module directory, whichever root manager installed it. */
    static final String FIND =
        "for d in /data/adb/modules/mora_audio_suite /data/adb/ksu/modules/mora_audio_suite /data/adb/ap/modules/mora_audio_suite; do "
        + "[ -d \"$d\" ] && break; done; [ -d \"$d\" ] || { echo '{\"status\":\"error\",\"message\":\"module not found\"}'; exit 3; }; ";

    static final class Result {
        final int code;
        final String out;
        Result(int code, String out) { this.code = code; this.out = out; }
        boolean ok() { return code == 0; }
    }

    static Result run(String command) {
        Process p = null;
        try {
            p = new ProcessBuilder("su").redirectErrorStream(true).start();
            OutputStream os = p.getOutputStream();
            os.write((command + "\nexit\n").getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            int code = p.waitFor();
            return new Result(code, sb.toString().trim());
        } catch (Exception e) {
            return new Result(127, "su unavailable: " + e.getMessage());
        } finally {
            if (p != null) p.destroy();
        }
    }

    /** Runs a script that lives inside the Mora module. */
    static Result module(String script, String args) {
        return run(FIND + "sh \"$d/" + script + "\" " + args);
    }
}
