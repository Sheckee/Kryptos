package kryptos.ui;

import arc.Core;
import arc.util.Http;
import arc.util.Log;
import arc.util.serialization.Jval;
import mindustry.Vars;
import mindustry.mod.Mods.LoadedMod;

/**
 * Pings the GitHub releases API once per game launch. If the latest release
 * tag is newer than the installed mod version, shows a popup with the
 * changelog and a one-tap update button. Fails silently offline.
 */
public class KryptosUpdateChecker {
    private static final String REPO = "Ains-Code/Kryptos";
    private static final String API_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";

    public static void check() {
        LoadedMod self = Vars.mods.getMod(kryptos.KryptosMod.class);
        if (self == null || self.meta.version == null) {
            return;
        }

        String currentVersionStr = self.meta.version;
        int[] currentVersion = parseVersion(currentVersionStr);

        Http.get(API_URL)
                .timeout(6000)
                .error(err -> Log.debug("[Kryptos] Update check failed: @", err.toString()))
                .submit(res -> {
                    try {
                        Jval json = Jval.read(res.getResultAsString());
                        String tag = json.getString("tag_name", "");
                        String body = json.getString("body", "");
                        String htmlUrl = json.getString("html_url", "");

                        int[] latestVersion = parseVersion(tag);

                        if (isGreater(latestVersion, currentVersion)) {
                            Core.app.post(() -> new KryptosUpdateDialog(currentVersionStr, tag, body, htmlUrl).show());
                        }
                    } catch (Exception e) {
                        Log.debug("[Kryptos] Failed to parse release info: @", e.toString());
                    }
                });
    }

    private static int[] parseVersion(String version) {
        try {
            String clean = version.replaceAll("[^0-9.]", "");
            if (clean.isEmpty()) {
                return new int[0];
            }
            String[] parts = clean.split("\\.");
            int[] numbers = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                numbers[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
            }
            return numbers;
        } catch (Exception e) {
            return new int[0];
        }
    }

    private static boolean isGreater(int[] a, int[] b) {
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) {
                return av > bv;
            }
        }
        return false;
    }
}
