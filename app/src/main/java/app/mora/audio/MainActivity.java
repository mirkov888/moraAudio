// Mora Audio Suite - made by Mora & Claude Code
package app.mora.audio;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Mora Audio: a root app that drives the Mora Audio Suite module (same scripts the module WebUI uses). */
public class MainActivity extends Activity {
    private static final int BG = 0xFF0B0F14;
    private static final int CARD = 0xFF141A22;
    private static final int CARD2 = 0xFF1B232D;
    private static final int TEXT = 0xFFEAF0F6;
    private static final int MUTED = 0xFF8794A3;
    private static final int ACCENT = 0xFF3DD6C6;
    private static final int ACCENT2 = 0xFF7C9CFF;
    private static final int BAD = 0xFFFF6B6B;
    private static final int OK = 0xFF5BD68A;
    private static final int WARN = 0xFFFFC857;

    private static final String[] MODES = {"audiophile", "movie", "power_saver"};
    private static final String[] MODE_TITLES = {"Musik", "Film", "Hemat"};
    private static final String[] MODE_DESC = {
        "Hi-Res jernih untuk musik dan DAC/IEM.",
        "Dialog jelas dan ruang suara lebih luas.",
        "Efek ringan, hemat baterai."};
    private static final String[] TAB_NAMES = {"Beranda", "Suara", "Tuning", "Sistem"};

    /** A control bound to a field of the status JSON and a module config key. */
    private static final class Ctl {
        final String field, key, label, unit;
        final int min, step;
        TextView valueLabel;
        Ctl(String field, String key, String label, int min, int step, String unit) {
            this.field = field; this.key = key; this.label = label; this.min = min; this.step = step; this.unit = unit;
        }
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<View> pages = new ArrayList<>();
    private final TextView[] tabs = new TextView[4];
    private final View[] tabBars = new View[4];
    private final LinearLayout[] modeCards = new LinearLayout[3];
    private final List<View> bound = new ArrayList<>();

    private TextView heroTitle, heroSub, heroChip, statModule, statAndroid, statRys, statBass;
    private TextView fixBtn, logView;
    private LinearLayout hero;
    private boolean binding;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(CARD);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);

        FrameLayout body = new FrameLayout(this);
        pages.add(page(body, buildHome()));
        pages.add(page(body, buildSound()));
        pages.add(page(body, buildTuning()));
        pages.add(page(body, buildSystem()));
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(buildTabBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        setContentView(root);
        showTab(0);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (heroTitle != null) refresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    // ------------------------------------------------------------------ pages
    private View page(FrameLayout parent, LinearLayout content) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        content.setPadding(dp(16), dp(14), dp(16), dp(24));
        sv.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return sv;
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    private LinearLayout buildHome() {
        LinearLayout c = column();
        ImageView banner = new ImageView(this);
        banner.setImageResource(R.drawable.mora_banner);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        banner.setContentDescription("Mora Audio banner");
        banner.setBackgroundColor(CARD);
        banner.setClipToOutline(true);
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(176));
        bannerLp.setMargins(0, 0, 0, dp(16));
        c.addView(banner, bannerLp);
        c.addView(label("Mora Audio", 28, TEXT, true));
        c.addView(label("Dengar lebih jernih, tonton lebih hidup", 13, MUTED, false));

        hero = column();
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF12453F, 0xFF1B2A5E});
        g.setCornerRadius(dp(24));
        hero.setBackground(g);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.setMargins(0, dp(16), 0, 0);
        hero.setLayoutParams(hlp);
        heroChip = label("VIPER4ANDROID", 11, ACCENT, true);
        heroChip.setLetterSpacing(0.12f);
        hero.addView(heroChip);
        heroTitle = label("Memeriksa driver...", 22, TEXT, true);
        heroTitle.setPadding(0, dp(6), 0, 0);
        hero.addView(heroTitle);
        heroSub = label("", 13, 0xFFB8C4D2, false);
        heroSub.setPadding(0, dp(6), 0, 0);
        hero.addView(heroSub);
        fixBtn = button("Perbaiki driver", true);
        fixBtn.setVisibility(View.GONE);
        fixBtn.setOnClickListener(v -> fixDriver());
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        flp.setMargins(0, dp(14), 0, 0);
        hero.addView(fixBtn, flp);
        c.addView(hero);

        c.addView(section("Mode dengar"));
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout m = column();
            m.setPadding(dp(16), dp(14), dp(16), dp(14));
            m.addView(label(MODE_TITLES[i], 16, TEXT, true));
            m.addView(label(MODE_DESC[i], 13, MUTED, false));
            m.setOnClickListener(v -> setMode(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(6), 0, 0);
            m.setLayoutParams(lp);
            styleMode(m, false);
            modeCards[i] = m;
            c.addView(m);
        }

        c.addView(section("Ringkasan"));
        LinearLayout stats = card();
        statModule = stat(stats, "Module");
        statAndroid = stat(stats, "Android");
        statRys = stat(stats, "RySound");
        statBass = stat(stats, "BassBoost");
        c.addView(stats);
        return c;
    }

    private LinearLayout buildSound() {
        LinearLayout c = column();
        c.addView(label("Suara", 28, TEXT, true));
        c.addView(label("Bass dan loudness bekerja di semua aplikasi", 13, MUTED, false));
        c.addView(section("Bass Boost"));
        LinearLayout a = card();
        a.addView(toggle(new Ctl("cfg_bb_bass", "BB_BASS", "Bass Boost", 0, 1, "")));
        a.addView(slider(new Ctl("cfg_bb_strength", "BB_STRENGTH", "Kekuatan bass", 0, 50, ""), 1000));
        c.addView(a);
        c.addView(section("Pita rendah"));
        LinearLayout b = card();
        b.addView(toggle(new Ctl("cfg_bb_dp", "BB_DP", "Dynamics (low band)", 0, 1, "")));
        b.addView(slider(new Ctl("cfg_bb_gain", "BB_GAIN", "Gain pita rendah", 0, 1, " dB"), 15));
        c.addView(b);
        c.addView(section("Loudness"));
        LinearLayout d = card();
        d.addView(toggle(new Ctl("cfg_bb_loudness", "BB_LOUDNESS", "Loudness Enhancer", 0, 1, "")));
        d.addView(slider(new Ctl("cfg_bb_target", "BB_TARGET", "Target loudness", 0, 50, " mB"), 1000));
        c.addView(d);
        c.addView(label("Perubahan langsung terdengar. Jika tidak ada efek, restart sekali setelah memasang module.", 12, MUTED, false));
        return c;
    }

    private LinearLayout buildTuning() {
        LinearLayout c = column();
        c.addView(label("Tuning", 28, TEXT, true));
        c.addView(label("Opsi sistem audio", 13, MUTED, false));
        c.addView(section("Volume"));
        LinearLayout v = card();
        v.addView(toggle(new Ctl("cfg_volsteps100", "VOLSTEPS100", "Volume 100 langkah", 0, 1, "")));
        v.addView(toggle(new Ctl("cfg_safevol_off", "SAFEVOL_OFF", "Matikan peringatan volume", 0, 1, "")));
        c.addView(v);
        c.addView(section("Efek"));
        LinearLayout e = card();
        e.addView(toggle(new Ctl("cfg_spatializer_off", "SPATIALIZER_OFF", "Matikan spatializer", 0, 1, "")));
        e.addView(toggle(new Ctl("cfg_vollistener_off", "VOLLISTENER_OFF", "Matikan volume listener", 0, 1, "")));
        e.addView(toggle(new Ctl("cfg_rysound", "RYSOUND", "Daftarkan efek RySound", 0, 1, "")));
        c.addView(e);
        c.addView(label("Sebagian opsi aktif penuh setelah restart audio atau reboot.", 12, MUTED, false));
        TextView apply = button("Terapkan sekarang", true);
        apply.setOnClickListener(x -> applyNow());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.setMargins(0, dp(16), 0, 0);
        c.addView(apply, lp);
        return c;
    }

    private LinearLayout buildSystem() {
        LinearLayout c = column();
        c.addView(label("Sistem", 28, TEXT, true));
        c.addView(label("Diagnosa dan perbaikan", 13, MUTED, false));
        c.addView(section("Aksi"));
        c.addView(action("Perbaiki driver ViPER", "Relabel, daftarkan ulang, restart audio", v -> fixDriver()));
        c.addView(action("Restart audio", "Mulai ulang audioserver", v -> restartAudio()));
        c.addView(action("Muat ulang status", "Baca ulang dari module", v -> refresh()));
        c.addView(section("Log"));
        logView = label("Ketuk Lihat log untuk membaca log module.", 12, MUTED, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        LinearLayout lc = card();
        lc.addView(logView);
        c.addView(lc);
        c.addView(action("Lihat log", "40 baris terakhir + diagnosa driver", v -> showLog()));
        c.addView(section("Tentang"));
        LinearLayout ab = card();
        ab.addView(label("Mora Audio Suite", 15, TEXT, true));
        ab.addView(label("Dibuat oleh Mora & Claude Code. Komponen pihak ketiga tercantum di THIRD_PARTY_NOTICES.md pada module.", 12, MUTED, false));
        c.addView(ab);
        return c;
    }

    private View buildTabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(CARD);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            View ind = new View(this);
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(ACCENT);
            gd.setCornerRadius(dp(2));
            ind.setBackground(gd);
            cell.addView(ind, new LinearLayout.LayoutParams(dp(28), dp(3)));
            TextView t = label(TAB_NAMES[i], 13, MUTED, true);
            t.setGravity(Gravity.CENTER);
            cell.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            cell.setOnClickListener(v -> showTab(idx));
            tabs[i] = t;
            tabBars[i] = ind;
            bar.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        return bar;
    }

    private void showTab(int idx) {
        for (int i = 0; i < pages.size(); i++) pages.get(i).setVisibility(i == idx ? View.VISIBLE : View.GONE);
        for (int i = 0; i < 4; i++) {
            tabs[i].setTextColor(i == idx ? ACCENT : MUTED);
            tabBars[i].setVisibility(i == idx ? View.VISIBLE : View.INVISIBLE);
        }
    }

    // ------------------------------------------------------------------ widgets
    private TextView label(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView section(String s) {
        TextView t = label(s.toUpperCase(), 11, MUTED, true);
        t.setPadding(dp(4), dp(22), 0, dp(4));
        t.setLetterSpacing(0.1f);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = column();
        c.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(20));
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, 0);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView button(String s, boolean primary) {
        TextView b = label(s, 15, primary ? 0xFF062623 : TEXT, true);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary ? ACCENT : CARD2);
        g.setCornerRadius(dp(14));
        b.setBackground(g);
        b.setClickable(true);
        return b;
    }

    private View action(String title, String sub, View.OnClickListener l) {
        LinearLayout r = column();
        r.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(18));
        r.setBackground(g);
        r.addView(label(title, 15, TEXT, true));
        r.addView(label(sub, 12, MUTED, false));
        r.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, 0);
        r.setLayoutParams(lp);
        return r;
    }

    private TextView stat(LinearLayout parent, String name) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(8), 0, dp(8));
        TextView n = label(name, 14, MUTED, false);
        TextView v = label("-", 14, TEXT, true);
        v.setGravity(Gravity.END);
        r.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(r);
        return v;
    }

    private void styleMode(LinearLayout m, boolean active) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(active ? 0xFF123B37 : CARD);
        g.setCornerRadius(dp(18));
        g.setStroke(dp(active ? 2 : 1), active ? ACCENT : CARD2);
        m.setBackground(g);
    }

    private View toggle(final Ctl c) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(8), 0, dp(8));
        r.addView(label(c.label, 15, TEXT, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch s = new Switch(this);
        s.setTag(c);
        s.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{ACCENT, 0xFF8794A3}));
        s.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{0xFF1E6E66, 0xFF2A3440}));
        s.setOnCheckedChangeListener((v, on) -> {
            if (!binding) sendConfig(c.key, on ? "1" : "0");
        });
        bound.add(s);
        r.addView(s);
        return r;
    }

    private View slider(final Ctl c, int max) {
        LinearLayout r = column();
        r.setPadding(0, 0, 0, dp(6));
        c.valueLabel = label(c.label, 12, MUTED, false);
        r.addView(c.valueLabel);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max / c.step);
        sb.setTag(c);
        sb.setProgressTintList(ColorStateList.valueOf(ACCENT));
        sb.setThumbTintList(ColorStateList.valueOf(ACCENT));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                c.valueLabel.setText(c.label + ": " + (p * c.step) + c.unit);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) {
                if (!binding) sendConfig(c.key, String.valueOf(s.getProgress() * c.step));
            }
        });
        bound.add(sb);
        r.addView(sb);
        return r;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------ state
    private void refresh() {
        heroTitle.setText("Memeriksa driver...");
        io.execute(() -> {
            Root.Result r = Root.module("get_audio_status.sh", "");
            JSONObject j = null;
            String err = null;
            if (r.ok()) {
                try {
                    int a = r.out.indexOf('{');
                    j = new JSONObject(r.out.substring(Math.max(a, 0)));
                } catch (Exception e) {
                    err = "Status tidak terbaca";
                }
            } else {
                err = r.out.isEmpty() ? "Akses root ditolak atau module belum terpasang" : r.out;
            }
            final JSONObject fj = j;
            final String ferr = err;
            ui.post(() -> showState(fj, ferr));
        });
    }

    private static String reasonText(String reason) {
        switch (reason) {
            case "not_mounted": return "Root manager tidak me-mount file module. Tekan Perbaiki driver.";
            case "ignore_effects": return "Properti ro.audio.ignore_effects mematikan efek. Tekan Perbaiki driver.";
            case "not_registered": return "ROM belum memuat konfigurasi ViPER. Tekan Perbaiki driver.";
            case "no_module_lib": return "Library ViPER tidak ada. Flash ulang module.";
            case "no_audioserver": return "audioserver tidak berjalan. Reboot.";
            case "not_loaded": return "Terdaftar tetapi belum dimuat. Tekan Perbaiki driver, lalu putar audio.";
            default: return "";
        }
    }

    private void showState(JSONObject j, String err) {
        if (j == null) {
            heroTitle.setText("Tidak terhubung");
            heroTitle.setTextColor(BAD);
            heroSub.setText(err);
            fixBtn.setVisibility(View.GONE);
            return;
        }
        boolean loaded = j.optInt("viper_loaded") == 1;
        boolean reg = j.optInt("viper_registered") == 1;
        String reason = j.optString("viper_reason", "");
        if (loaded) {
            heroTitle.setText("Driver aktif");
            heroTitle.setTextColor(OK);
            heroSub.setText("Buka ViPER4Android lalu putar musik. Driver Status seharusnya Normal.");
            fixBtn.setVisibility(View.GONE);
        } else {
            heroTitle.setText(reg ? "Terdaftar, belum dimuat" : "Driver belum aktif");
            heroTitle.setTextColor(reg ? WARN : BAD);
            String t = reasonText(reason);
            heroSub.setText(t.isEmpty() ? "Tekan Perbaiki driver, lalu putar audio." : t);
            fixBtn.setVisibility(View.VISIBLE);
        }
        statModule.setText("v" + j.optString("version"));
        statAndroid.setText(j.optString("android") + "  " + j.optString("device"));
        statRys.setText(j.optString("rys_mode", "-"));
        statBass.setText(j.optInt("bb_installed") == 1 ? "aktif" : "restart dulu");

        String mode = j.optString("mode");
        for (int i = 0; i < 3; i++) styleMode(modeCards[i], MODES[i].equals(mode));

        binding = true;
        for (View v : bound) {
            Ctl c = (Ctl) v.getTag();
            if (v instanceof Switch) {
                ((Switch) v).setChecked(j.optInt(c.field) == 1);
            } else if (v instanceof SeekBar) {
                ((SeekBar) v).setProgress(Math.max(0, j.optInt(c.field) / c.step));
            }
        }
        binding = false;
    }

    // ------------------------------------------------------------------ actions
    private void sendConfig(final String key, final String value) {
        io.execute(() -> {
            Root.Result r = key.startsWith("BB_")
                ? Root.module("bass_ctl.sh", "set " + key + " " + value)
                : Root.module("mora_cfg.sh", "set " + key + " " + value);
            final boolean ok = r.ok() && r.out.toLowerCase(java.util.Locale.ROOT).contains("success");
            ui.post(() -> toast(ok ? "Tersimpan" : "Gagal menyimpan " + key));
        });
    }

    private void setMode(final int idx) {
        for (int i = 0; i < 3; i++) styleMode(modeCards[i], i == idx);
        toast("Menerapkan mode " + MODE_TITLES[idx] + "...");
        io.execute(() -> {
            Root.Result r = Root.module("set_audio_mode.sh", MODES[idx]);
            final boolean ok = r.ok() && r.out.toLowerCase(java.util.Locale.ROOT).contains("success");
            ui.post(() -> {
                toast(ok ? "Mode " + MODE_TITLES[idx] + " aktif" : "Gagal mengganti mode");
                refresh();
            });
        });
    }

    private void fixDriver() {
        toast("Memperbaiki driver...");
        heroTitle.setText("Memperbaiki...");
        io.execute(() -> {
            Root.Result r = Root.module("viper_fix.sh", "fix");
            String after = "", hint = "";
            try {
                String line = r.out.substring(r.out.lastIndexOf('{'));
                JSONObject j = new JSONObject(line);
                after = j.optString("after");
                hint = j.optString("hint");
            } catch (Exception ignored) { }
            final boolean ok = "ok".equals(after);
            final String fh = hint;
            ui.post(() -> {
                toast(ok ? "Driver aktif" : (fh.isEmpty() ? "Perbaikan gagal" : fh));
                refresh();
            });
        });
    }

    private void restartAudio() {
        toast("Restart audio...");
        io.execute(() -> {
            Root.Result r = Root.run("setprop ctl.restart audioserver");
            final boolean ok = r.ok();
            ui.post(() -> {
                toast(ok ? "Perintah restart audio dikirim" : "Gagal restart audio: akses root ditolak");
                refresh();
            });
        });
    }

    private void showLog() {
        logView.setText("Memuat...");
        io.execute(() -> {
            Root.Result r = Root.run("tail -n 40 /data/adb/mora_audio_suite.log 2>&1; echo; echo \"SELinux: $(getenforce 2>&1)\"; "
                + Root.FIND + "sh \"$d/viper_status.sh\" 2>&1");
            final String out = r.out.isEmpty() ? "Log belum ada. Reboot setelah memasang module." : r.out;
            ui.post(() -> logView.setText(out));
        });
    }

    private void applyNow() {
        toast("Menerapkan...");
        io.execute(() -> {
            Root.Result a = Root.module("mora_cfg.sh", "apply");
            Root.Result b = Root.module("bass_ctl.sh", "apply");
            final boolean ok = a.ok() && b.ok();
            ui.post(() -> {
                toast(ok ? "Diterapkan" : "Gagal menerapkan");
                refresh();
            });
        });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
