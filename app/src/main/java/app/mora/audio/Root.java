// Mora Audio Suite - root bridge for the optional Mora Audio Suite module.
package app.mora.audio;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Small internal root bridge. Only fixed app-owned commands should call run().
 * Never expose this class or arbitrary shell input through an exported component.
 */
final class Root {
    /** Finds the module directory used by Magisk, KernelSU and APatch. */
    static final String FIND =
        "for d in /data/adb/modules/mora_audio_suite /data/adb/ksu/modules/mora_audio_suite /data/adb/ap/modules/mora_audio_suite; do "
        + "[ -d \"$d\" ] && break; done; "
        + "[ -n \"$d\" ] && [ -d \"$d\" ] || { echo '{\"status\":\"error\",\"message\":\"module not found\"}'; exit 3; }; ";

    static final class Result {
        final int code;
        final String out;
        Result(int code, String out) { this.code = code; this.out = out; }
        boolean ok() { return code == 0; }
    }

    /** Executes an app-owned command through the standard su -c interface. */
    static Result run(String command) {
        Process p = null;
        try {
            p = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (sb.length() < 65536) sb.append(line).append('\n');
            }
            int code = p.waitFor();
            return new Result(code, sb.toString().trim());
        } catch (Exception e) {
            return new Result(127, "su unavailable: " + e.getMessage());
        } finally {
            if (p != null) p.destroy();
        }
    }

    /** Runs a known script inside the installed module; arguments are deliberately restricted. */
    static Result module(String script, String args) {
        if (script == null || !script.matches("[A-Za-z0-9_.-]+\\.sh")) {
            return new Result(2, "invalid module script");
        }
        if (args == null) args = "";
        // Current app commands use only fixed words, numbers, dots, underscores and spaces.
        if (!args.matches("[A-Za-z0-9_. -]*")) {
            return new Result(2, "invalid module arguments");
        }
        return run(FIND + "sh \"$d/" + script + "\" " + args);
    }

    private Root() { }
}
