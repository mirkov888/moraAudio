// Mora Audio Suite - made by Mora & Claude Code
package app.mora.audio;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.WindowInsets;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.AudioDeviceInfo;
import android.media.AudioPlaybackConfiguration;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.content.ComponentName;
import android.content.Intent;
import android.provider.Settings;
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
    private static final int BG = 0xFF0D1117;
    private static final int CARD = 0xFF171E28;
    private static final int CARD2 = 0xFF222C38;
    private static final int TEXT = 0xFFE4E9F2;
    private static final int MUTED = 0xFFA8B2C2;
    private static final int ACCENT = 0xFF9ADFD6;
    private static final int ACCENT2 = 0xFFB5C7FF;
    private static final int BAD = 0xFFFF6B6B;
    private static final int OK = 0xFF5BD68A;
    private static final int WARN = 0xFFFFC857;
    private static final String ACTION_USB_PERMISSION = "app.mora.audio.USB_PERMISSION";

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
    private final List<LinearLayout> contents = new ArrayList<>();
    private final TextView[] tabs = new TextView[4];
    private final View[] tabPills = new View[4];
    private final ImageView[] tabIcons = new ImageView[4];
    private static final int[] TAB_ICONS = {R.drawable.ic_tab_home, R.drawable.ic_tab_sound, R.drawable.ic_tab_tune, R.drawable.ic_tab_system};
    private FrameLayout rootView;
    private AuroraView aurora;
    private View tabBar, topScrim, bottomScrim;
    private int insetTop, insetBottom, curTab = -1;
    private final LinearLayout[] modeCards = new LinearLayout[3];
    private final List<View> bound = new ArrayList<>();

    private TextView heroTitle, heroSub, heroChip, statModule, statAndroid, statRys, statBass, statDriver, statMode, statAudioServer, statOutput;
    private TextView fixBtn, logView, nowTitle, nowArtist, nowState, nowOutput, nowQuality, mediaPermissionBtn;
    private long lastQualityRead;
    private final Handler liveHandler = new Handler(Looper.getMainLooper());
    private boolean livePolling;
    private final Runnable livePoll = new Runnable() { public void run() { if (livePolling) { refreshNowPlaying(); liveHandler.postDelayed(this, 1000); } } };
    private LinearLayout hero;
    private boolean binding;
    private boolean usbReceiverRegistered;
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                String name = device == null ? "USB DAC" : String.valueOf(device.getProductName());
                UsbManager manager = (UsbManager) getSystemService(USB_SERVICE);
                UsbDevice audioDevice = UsbHostInspector.findAudioStreamingDevice(manager);
                String result = granted
                    ? "Izin USB diberikan untuk " + name + ".\n\n"
                        + UsbHostInspector.inspect(manager) + "\n\n"
                        + UsbHostInspector.inspectRawDescriptors(manager, device != null ? device : audioDevice)
                    : "Izin USB ditolak untuk " + name + ". Sambungkan ulang DAC atau izinkan akses saat diminta.";
                if (logView != null) logView.setText(result);
                toast(granted ? "Izin USB diberikan" : "Izin USB ditolak");
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)
                    || UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (logView != null) logView.setText(
                    UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)
                        ? "Perangkat USB tersambung. Ketuk Periksa perangkat USB untuk meminta izin."
                        : "Perangkat USB dilepas.");
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        android.view.Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }

        FrameLayout root = new FrameLayout(this);
        rootView = root;
        aurora = new AuroraView(this);
        root.addView(aurora, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout body = new FrameLayout(this);
        pages.add(page(body, buildHome()));
        pages.add(page(body, buildSound()));
        pages.add(page(body, buildTuning()));
        pages.add(page(body, buildSystem()));
        root.addView(body, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        topScrim = new View(this);
        topScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xE6070B11, 0x00070B11}));
        root.addView(topScrim, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(28), Gravity.TOP));
        bottomScrim = new View(this);
        bottomScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
            new int[]{0xF2070B11, 0x00070B11}));
        root.addView(bottomScrim, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(140), Gravity.BOTTOM));

        tabBar = buildTabBar();
        FrameLayout.LayoutParams tabParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(68), Gravity.BOTTOM);
        tabParams.setMargins(dp(16), 0, dp(16), dp(12));
        root.addView(tabBar, tabParams);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets system = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                insetTop = system.top;
                insetBottom = system.bottom;
            } else {
                insetTop = insets.getSystemWindowInsetTop();
                insetBottom = insets.getSystemWindowInsetBottom();
            }
            applyInsets();
            return insets;
        });
        setContentView(root);
        applyInsets();
        root.requestApplyInsets();
        registerUsbReceiver();
        showTab(0);
        refresh();
    }

    private void applyInsets() {
        for (LinearLayout content : contents) {
            content.setPadding(dp(16), insetTop + dp(16), dp(16), insetBottom + dp(112));
        }
        if (topScrim != null && topScrim.getLayoutParams() != null) {
            topScrim.getLayoutParams().height = insetTop + dp(28);
            topScrim.requestLayout();
        }
        if (bottomScrim != null && bottomScrim.getLayoutParams() != null) {
            bottomScrim.getLayoutParams().height = insetBottom + dp(140);
            bottomScrim.requestLayout();
        }
        if (tabBar != null && tabBar.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) tabBar.getLayoutParams();
            params.bottomMargin = insetBottom + dp(12);
            tabBar.setLayoutParams(params);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (aurora != null) aurora.setRunning(true);
        if (heroTitle != null) { refresh(); startLivePolling(); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (aurora != null) aurora.setRunning(false);
        livePolling = false;
        liveHandler.removeCallbacks(livePoll);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        livePolling = false;
        liveHandler.removeCallbacks(livePoll);
        if (usbReceiverRegistered) {
            unregisterReceiver(usbReceiver);
            usbReceiverRegistered = false;
        }
        io.shutdownNow();
    }

    // ------------------------------------------------------------------ pages
    private View page(FrameLayout parent, LinearLayout content) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        contents.add(content);
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
        hero.setBackground(glassSurface(0xFF203A4B, 0x669ADFD6, true));
        hero.setElevation(dp(3));
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
        statDriver = stat(stats, "ViPER driver");
        statMode = stat(stats, "Mode aktif");
        statAudioServer = stat(stats, "Audio server");
        statOutput = stat(stats, "Audio output");
        c.addView(stats);
        c.addView(section("Statistik sistem audio"));
        c.addView(label("Data dibaca langsung dari Android dan Mora Audio Suite saat halaman diperbarui.", 12, MUTED, false));
        TextView refreshStats = button("Perbarui statistik", true);
        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        statsLp.setMargins(0, dp(10), 0, 0);
        refreshStats.setOnClickListener(v -> refresh());
        c.addView(refreshStats, statsLp);
        c.addView(section("Sedang diputar"));
        LinearLayout playing = card();
        nowTitle = label("Belum ada media aktif", 18, TEXT, true);
        nowArtist = label("Putar musik atau video di aplikasi mana pun", 13, MUTED, false);
        nowState = label("Status: menunggu", 12, ACCENT, true);
        nowOutput = label("Output: -", 12, MUTED, false);
        nowQuality = label("Kualitas output: membaca data...", 12, MUTED, false);
        playing.addView(nowTitle);
        nowArtist.setPadding(0, dp(5), 0, dp(8));
        playing.addView(nowArtist);
        playing.addView(nowState);
        playing.addView(nowOutput);
        nowQuality.setPadding(0, dp(5), 0, 0);
        playing.addView(nowQuality);
        mediaPermissionBtn = button("Aktifkan akses media", true);
        LinearLayout.LayoutParams permLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        permLp.setMargins(0, dp(10), 0, 0);
        mediaPermissionBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        playing.addView(mediaPermissionBtn, permLp);
        c.addView(playing);
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
        c.addView(section("Diagnostik modul v3.4+"));
        c.addView(action("Periksa pemasangan", "Root, mount, HAL, ViPER, konflik dan SELinux", v -> showModuleCheck()));
        c.addView(action("Laporan lengkap", "Baca laporan diagnostik tanpa mengubah pengaturan", v -> showModuleReport()));
        c.addView(action("Pantau suhu dan baterai", "Baca monitor bawaan modul", v -> showModuleMonitor()));
        c.addView(section("USB DAC / UAC"));
        c.addView(action("Periksa perangkat USB", "Baca descriptor USB dan deteksi interface AudioStreaming", v -> showUsbHostReport()));
        c.addView(section("Cadangan pengaturan"));
        c.addView(action("Ekspor cadangan", "Simpan konfigurasi ke Download/mora_backup.tar.gz", v -> exportModuleBackup()));
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
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        bar.setBackground(glass(34, 0, 1.6f, 0xB30B1119));
        bar.setElevation(dp(12));
        for (int i = 0; i < 4; i++) {
            final int index = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout pill = new FrameLayout(this);
            ImageView icon = new ImageView(this);
            icon.setImageResource(TAB_ICONS[i]);
            icon.setContentDescription(TAB_NAMES[i]);
            pill.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            cell.addView(pill, new LinearLayout.LayoutParams(dp(60), dp(32)));
            TextView title = label(TAB_NAMES[i], 12, MUTED, true);
            title.setGravity(Gravity.CENTER);
            cell.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.setOnClickListener(v -> showTab(index));
            tabs[i] = title;
            tabPills[i] = pill;
            tabIcons[i] = icon;
            bar.addView(cell, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        return bar;
    }

    private void showTab(int idx) {
        boolean changed = curTab != idx;
        curTab = idx;
        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).setVisibility(i == idx ? View.VISIBLE : View.GONE);
        }
        for (int i = 0; i < 4; i++) {
            boolean selected = i == idx;
            tabs[i].setTextColor(selected ? TEXT : MUTED);
            tabIcons[i].setColorFilter(selected ? 0xFFC8FFF8 : MUTED);
            if (selected) {
                GradientDrawable pill = new GradientDrawable();
                pill.setColor(0x403DD6C6);
                pill.setCornerRadius(dp(16));
                pill.setStroke(dp(1), 0x663DD6C6);
                tabPills[i].setBackground(pill);
            } else {
                tabPills[i].setBackground(null);
            }
        }
        if (changed) {
            View page = pages.get(idx);
            page.setAlpha(0f);
            page.animate().alpha(1f).setDuration(220).start();
        }
    }

    // ------------------------------------------------------------------ widgets
    private Drawable glass(int radiusDp, int tint, float strength, int under) {
        return new GlassDrawable(dp(radiusDp), Math.max(1, dp(1)), tint, strength, under);
    }

    private Drawable ripple(Drawable content, int radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, mask);
    }

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
        c.setPadding(dp(18), dp(10), dp(18), dp(10));
        c.setBackground(glass(24, 0, 1f, 0));
        c.setElevation(dp(1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, 0);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView button(String s, boolean primary) {
        MaterialButton b = new MaterialButton(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setCornerRadius(dp(24));
        b.setTextColor(primary ? 0xFF00201D : TEXT);
        b.setBackgroundTintList(ColorStateList.valueOf(primary ? ACCENT : 0x26FFFFFF));
        if (!primary) {
            b.setStrokeWidth(dp(1));
            b.setStrokeColor(ColorStateList.valueOf(0x40FFFFFF));
        }
        b.setRippleColor(ColorStateList.valueOf(primary ? 0x33FFFFFF : 0x22FFFFFF));
        return b;
    }

    private View action(String title, String sub, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(14), dp(14), dp(14));
        row.setBackground(ripple(glass(22, 0, 1f, 0), 22));
        LinearLayout text = column();
        text.addView(label(title, 16, TEXT, true));
        text.addView(label(sub, 13, MUTED, false));
        row.addView(text, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label("\u203A", 26, MUTED, false));
        row.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, 0);
        row.setLayoutParams(lp);
        return row;
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

    private void styleMode(LinearLayout mode, boolean active) {
        mode.setBackground(ripple(active
            ? glass(22, ACCENT, 1.3f, 0) : glass(22, 0, 1f, 0), 22));
        if (mode.getChildCount() > 0 && mode.getChildAt(0) instanceof TextView) {
            ((TextView) mode.getChildAt(0)).setTextColor(active ? ACCENT : TEXT);
        }
    }

    private View toggle(final Ctl control) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        row.addView(label(control.label, 16, TEXT, false),
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setTag(control);
        toggle.setOnCheckedChangeListener((view, checked) -> {
            if (!binding) sendConfig(control.key, checked ? "1" : "0");
        });
        bound.add(toggle);
        row.addView(toggle);
        return row;
    }

    private View slider(final Ctl control, int max) {
        LinearLayout row = column();
        row.setPadding(0, dp(2), 0, dp(6));
        control.valueLabel = label(control.label, 13, MUTED, false);
        row.addView(control.valueLabel);
        Slider slider = new Slider(this);
        slider.setValueFrom(0f);
        slider.setValueTo((float) (max / control.step));
        slider.setStepSize(1f);
        slider.setValue(0f);
        slider.setTag(control);
        slider.setLabelBehavior(LabelFormatter.LABEL_GONE);
        slider.setTrackActiveTintList(ColorStateList.valueOf(ACCENT));
        slider.setThumbTintList(ColorStateList.valueOf(ACCENT));
        slider.setTrackInactiveTintList(ColorStateList.valueOf(0x33FFFFFF));
        slider.setHaloTintList(ColorStateList.valueOf(0x293DD6C6));
        slider.addOnChangeListener((view, value, fromUser) ->
            control.valueLabel.setText(control.label + ": "
                + (Math.round(value) * control.step) + control.unit));
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(Slider view) { }
            @Override public void onStopTrackingTouch(Slider view) {
                if (!binding) sendConfig(control.key,
                    String.valueOf(Math.round(view.getValue()) * control.step));
            }
        });
        bound.add(slider);
        row.addView(slider);
        return row;
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


    private void startLivePolling() {
        if (livePolling) return;
        livePolling = true;
        liveHandler.removeCallbacks(livePoll);
        liveHandler.post(livePoll);
    }

    private void refreshNowPlaying() {
        if (nowTitle == null) return;
        try {
            MediaSessionManager manager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            List<MediaController> sessions = manager.getActiveSessions(new ComponentName(this, MoraMediaListener.class));
            MediaController selected = null;
            for (MediaController controller : sessions) {
                android.media.session.PlaybackState state = controller.getPlaybackState();
                if (state != null && (state.getState() == android.media.session.PlaybackState.STATE_PLAYING ||
                    state.getState() == android.media.session.PlaybackState.STATE_BUFFERING)) {
                    selected = controller;
                    break;
                }
                if (selected == null && controller.getMetadata() != null) selected = controller;
            }
            if (selected == null) {
                nowTitle.setText("Tidak ada media aktif");
                nowArtist.setText("Putar musik/video untuk melihat status di sini");
                nowState.setText("Status: idle");
            } else {
                android.media.MediaMetadata metadata = selected.getMetadata();
                String title = metadata == null ? "" : metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);
                String artist = metadata == null ? "" : metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST);
                String album = metadata == null ? "" : metadata.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM);
                if (title == null || title.trim().isEmpty()) title = selected.getPackageName();
                if (artist == null || artist.trim().isEmpty()) artist = album == null ? selected.getPackageName() : album;
                nowTitle.setText(title);
                nowArtist.setText(artist);
                android.media.session.PlaybackState state = selected.getPlaybackState();
                int st = state == null ? android.media.session.PlaybackState.STATE_NONE : state.getState();
                String label = st == android.media.session.PlaybackState.STATE_PLAYING ? "SEDANG DIPUTAR" :
                    st == android.media.session.PlaybackState.STATE_PAUSED ? "PAUSED" :
                    st == android.media.session.PlaybackState.STATE_BUFFERING ? "BUFFERING" : "MEDIA AKTIF";
                nowState.setText("Status: " + label + "  •  " + selected.getPackageName());
            }
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            List<AudioPlaybackConfiguration> active = audio.getActivePlaybackConfigurations();
            AudioDeviceInfo[] devices = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            StringBuilder out = new StringBuilder("Output: ");
            int count = 0;
            for (AudioDeviceInfo device : devices) {
                if (count > 0) out.append(", ");
                out.append(device.getProductName());
                count++;
                if (count == 2) break;
            }
            if (count == 0) out.append("tidak terdeteksi");
            out.append("  •  Player aktif: ").append(active.size());
            nowOutput.setText(out.toString());
            mediaPermissionBtn.setVisibility(isMediaListenerEnabled() ? View.GONE : View.VISIBLE);
            long now = System.currentTimeMillis();
            if (now - lastQualityRead > 3000) {
                lastQualityRead = now;
                readAudioQuality(devices);
            }
        } catch (SecurityException e) {
            nowTitle.setText("Izin akses media belum aktif");
            nowArtist.setText("Aktifkan akses notifikasi/media agar aplikasi pemutar terdeteksi");
            mediaPermissionBtn.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            nowState.setText("Status media tidak tersedia");
        }
    }



    private static String audioEncodingName(int encoding) {
        switch (encoding) {
            case android.media.AudioFormat.ENCODING_PCM_16BIT: return "PCM 16-bit";
            case android.media.AudioFormat.ENCODING_PCM_8BIT: return "PCM 8-bit";
            case android.media.AudioFormat.ENCODING_PCM_FLOAT: return "PCM float";
            case android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED: return "PCM 24-bit";
            case android.media.AudioFormat.ENCODING_PCM_32BIT: return "PCM 32-bit";
            case android.media.AudioFormat.ENCODING_AC3: return "AC3";
            case android.media.AudioFormat.ENCODING_E_AC3: return "E-AC3";
            case android.media.AudioFormat.ENCODING_DTS: return "DTS";
            case android.media.AudioFormat.ENCODING_DTS_HD: return "DTS-HD";
            case android.media.AudioFormat.ENCODING_AAC_LC: return "AAC";
            case android.media.AudioFormat.ENCODING_AAC_HE_V1: return "AAC-HE";
            case android.media.AudioFormat.ENCODING_AAC_HE_V2: return "AAC-HEv2";
            default: return "Encoding " + encoding;
        }
    }

    private void readAudioQuality(AudioDeviceInfo[] devices) {
        io.execute(() -> {
            AudioDeviceInfo usb = null;
            if (devices != null) {
                for (AudioDeviceInfo device : devices) {
                    int type = device.getType();
                    if (type == AudioDeviceInfo.TYPE_USB_DEVICE || type == AudioDeviceInfo.TYPE_USB_HEADSET) {
                        usb = device;
                        break;
                    }
                }
            }
            String capability;
            if (usb == null) {
                capability = "USB DAC: tidak terdeteksi. Data speaker/Bluetooth tidak dipakai sebagai kemampuan DAC USB.";
            } else {
                StringBuilder b = new StringBuilder("USB DAC: ").append(usb.getProductName()).append("\nDukungan: ");
                int[] rates = usb.getSampleRates();
                int[] channels = usb.getChannelCounts();
                int[] encodings = usb.getEncodings();
                if (rates != null && rates.length > 0) {
                    java.util.Arrays.sort(rates);
                    b.append("sample rate ");
                    for (int i = 0; i < rates.length; i++) {
                        if (i > 0) b.append(", ");
                        b.append(rates[i] / 1000.0).append(" kHz");
                    }
                } else b.append("sample rate tidak diekspos Android");
                if (channels != null && channels.length > 0) {
                    b.append(" • channel ");
                    for (int i = 0; i < channels.length; i++) {
                        if (i > 0) b.append("/");
                        b.append(channels[i]);
                    }
                }
                if (encodings != null && encodings.length > 0) {
                    b.append(" • format ");
                    for (int i = 0; i < encodings.length; i++) {
                        if (i > 0) b.append(", ");
                        b.append(audioEncodingName(encodings[i]));
                    }
                }
                capability = b.toString();
            }
            Root.Result result = Root.run("dumpsys media.audio_flinger 2>/dev/null");
            String dump = result.out == null ? "" : result.out;
            String mixer = "Mixer aktif: tidak dapat dibaca";
            java.util.regex.Matcher rate = java.util.regex.Pattern.compile("(?im)^.*(?:sample rate|sampling rate)\\s*[:=]\\s*(\\d+)\\s*.*$").matcher(dump);
            if (rate.find()) {
                int hz = Integer.parseInt(rate.group(1));
                mixer = "Mixer aktif: " + (hz >= 1000 ? (hz / 1000.0) + " kHz" : hz + " Hz");
            }
            final String info = capability + "\n" + mixer
                + "\nCatatan: kemampuan DAC dan sample rate mixer bukan bukti bit-perfect.";
            ui.post(() -> { if (nowQuality != null) nowQuality.setText(info); });
        });
    }

    private boolean isMediaListenerEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName() + "/" + MoraMediaListener.class.getName());
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
        statBass.setText(j.optInt("bb_installed") == 1 ? "Terpasang" : "Belum terpasang");
        statDriver.setText(loaded ? "Loaded" : (reg ? "Registered" : "Tidak aktif"));
        statMode.setText(j.optString("mode", "-"));
        statAudioServer.setText("Memeriksa...");
        statOutput.setText("Memeriksa...");
        io.execute(() -> {
            Root.Result audio = Root.run("dumpsys media.audio_flinger 2>/dev/null");
            String dump = audio.out == null ? "" : audio.out;
            String server = dump.isEmpty() ? "Tidak tersedia" : "Data tersedia";
            String output = "Tidak terdeteksi";
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?im)^.*(?:sample rate|sampling rate|output device|device:|active tracks?).*$").matcher(dump);
            if (matcher.find()) output = matcher.group().trim();
            final String fs = server;
            final String fo = output;
            ui.post(() -> {
                if (statAudioServer != null) statAudioServer.setText(fs);
                if (statOutput != null) statOutput.setText(fo.length() > 70 ? fo.substring(0, 67) + "..." : fo);
            });
        });

        String mode = j.optString("mode");
        for (int i = 0; i < 3; i++) styleMode(modeCards[i], MODES[i].equals(mode));

        binding = true;
        for (View v : bound) {
            Ctl c = (Ctl) v.getTag();
            if (v instanceof MaterialSwitch) {
                ((MaterialSwitch) v).setChecked(j.optInt(c.field) == 1);
            } else if (v instanceof Slider) {
                ((Slider) v).setValue(Math.max(0, j.optInt(c.field) / c.step));
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

    // Optional v3.4+ commands. Older installed modules remain supported: missing scripts show a hint.
    private void showOptionalModuleOutput(String script, String args, String title) {
        if (logView != null) logView.setText("Memeriksa " + title + "...");
        showTab(3);
        io.execute(() -> {
            Root.Result result = Root.module(script, args);
            final String out = result.ok() ? result.out
                : "Fitur " + title + " belum tersedia atau gagal. Pastikan modul terbaru terpasang.\n" + result.out;
            ui.post(() -> {
                if (logView != null) logView.setText(out.isEmpty() ? "Tidak ada data." : out);
            });
        });
    }

    private void showModuleCheck() {
        showTab(3);
        logView.setText("Memeriksa instalasi module...");
        io.execute(() -> {
            // Prefer the module's own checklist; retain compatibility with older installs.
            Root.Result check = Root.module("setup_check.sh", "");
            String out;
            if (check.ok() && check.out != null && check.out.trim().startsWith("{")) {
                try {
                    JSONObject root = new JSONObject(check.out.trim());
                    org.json.JSONArray items = root.optJSONArray("items");
                    StringBuilder readable = new StringBuilder("=== PEMERIKSAAN MODUL ===\n");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.optJSONObject(i);
                            if (item == null) continue;
                            String status = item.optString("status", "warn").toUpperCase(java.util.Locale.ROOT);
                            readable.append(status).append("  ")
                                .append(item.optString("title", item.optString("id", "Pemeriksaan")))
                                .append("\n    ").append(item.optString("detail", "")).append("\n");
                        }
                    } else {
                        readable.append(check.out);
                    }
                    out = readable.toString();
                } catch (Exception parseError) {
                    out = "=== HASIL SETUP CHECK ===\n" + check.out;
                }
            } else {
                Root.Result status = Root.module("get_audio_status.sh", "");
                Root.Result viper = Root.module("viper_status.sh", "");
                Root.Result root = Root.run("id; echo; getenforce 2>/dev/null; echo; "
                    + "ls -ld /data/adb/modules/mora_audio_suite /data/adb/ksu/modules/mora_audio_suite "
                    + "/data/adb/ap/modules/mora_audio_suite 2>/dev/null");
                out = "=== ROOT / MODULE PATH (fallback) ===\n" + root.out
                    + "\n\n=== AUDIO STATUS ===\n" + status.out
                    + "\n\n=== VIPER STATUS ===\n" + viper.out
                    + "\n\nChecklist bawaan tidak tersedia (kemungkinan modul versi lama).";
            }
            final String result = out.length() > 60000 ? out.substring(0, 60000) + "\n[output dipotong]" : out;
            ui.post(() -> { if (logView != null) logView.setText(result); });
        });
    }

    private void showModuleReport() {
        showTab(3);
        logView.setText("Mengumpulkan laporan audio...");
        io.execute(() -> {
            // The bundled report is the authoritative, richer diagnostic source.
            Root.Result report = Root.module("report.sh", "");
            String out;
            if (report.ok() && report.out != null && !report.out.trim().isEmpty()) {
                out = report.out;
            } else {
                Root.Result status = Root.module("get_audio_status.sh", "");
                Root.Result viper = Root.module("viper_status.sh", "");
                Root.Result flinger = Root.run("dumpsys media.audio_flinger 2>/dev/null");
                out = "=== MORA AUDIO STATUS (fallback) ===\n" + status.out
                    + "\n\n=== VIPER DRIVER ===\n" + viper.out
                    + "\n\n=== AUDIO FLINGER ===\n" + flinger.out
                    + "\n\nLaporan bawaan tidak tersedia (kemungkinan modul versi lama).";
            }
            final String result = out.length() > 60000 ? out.substring(0, 60000) + "\n[output dipotong]" : out;
            ui.post(() -> { if (logView != null) logView.setText(result); });
        });
    }

    private void showModuleMonitor() {
        showTab(3);
        logView.setText("Membaca suhu dan baterai...");
        io.execute(() -> {
            // monitor.sh emits a compact JSON snapshot. Keep the Android fallback for old modules.
            Root.Result monitor = Root.module("monitor.sh", "");
            String out;
            if (monitor.ok() && monitor.out != null && monitor.out.trim().startsWith("{")) {
                try {
                    JSONObject data = new JSONObject(monitor.out.trim());
                    out = "=== MONITOR SISTEM ===\n"
                        + "Baterai: " + data.optInt("level") + "%\n"
                        + "Status: " + data.optString("status", "?") + "\n"
                        + "Suhu baterai: " + data.optInt("bat_temp") + " °C\n"
                        + "Suhu maksimum: " + data.optInt("max_temp") + " °C\n"
                        + "Arus: " + data.optInt("current_ma") + " mA\n"
                        + "Tegangan: " + data.optInt("voltage_mv") + " mV";
                } catch (Exception parseError) {
                    out = monitor.out;
                }
            } else {
                Root.Result fallback = Root.run("echo '=== BATTERY ==='; dumpsys battery 2>/dev/null; "
                    + "echo; echo '=== THERMAL ==='; dumpsys thermalservice 2>/dev/null; "
                    + "echo; echo '=== AUDIO SERVER ==='; pidof audioserver 2>/dev/null");
                out = fallback.out + "\n\nMonitor bawaan tidak tersedia (kemungkinan modul versi lama).";
            }
            final String result = out.isEmpty() ? "Data monitor tidak tersedia." : out;
            ui.post(() -> { if (logView != null) logView.setText(result); });
        });
    }

    private void registerUsbReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
        }
        usbReceiverRegistered = true;
    }

    private void showUsbHostReport() {
        showTab(3);
        UsbManager manager = (UsbManager) getSystemService(USB_SERVICE);
        if (manager == null) {
            logView.setText("USB Host tidak tersedia pada perangkat ini.");
            return;
        }
        UsbDevice device = UsbHostInspector.findAudioStreamingDevice(manager);
        if (device == null) {
            logView.setText(UsbHostInspector.inspect(manager)
                + "\n\nBelum ditemukan interface USB AudioStreaming (class 1 / subclass 2).");
            return;
        }
        if (manager.hasPermission(device)) {
            logView.setText(UsbHostInspector.inspect(manager) + "\n\n"
                + UsbHostInspector.inspectRawDescriptors(manager, device));
            return;
        }
        logView.setText("Meminta izin akses USB untuk "
            + String.valueOf(device.getProductName()) + "...\nKonfirmasi dialog Android yang muncul.");
        Intent permissionIntent = new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, permissionIntent, flags);
        manager.requestPermission(device, pendingIntent);
    }

    private void exportModuleBackup() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Ekspor cadangan?")
            .setMessage("Konfigurasi modul akan disimpan ke /sdcard/Download/mora_backup.tar.gz. File lama dengan nama sama dapat tertimpa.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Ekspor", (dialog, which) ->
                showOptionalModuleOutput("backup_ctl.sh", "export", "ekspor cadangan"))
            .show();
    }

    /** Builds the hero's lightweight liquid-glass surface without real-time blur. */
    private Drawable glassSurface(int fillColor, int tintColor, boolean prominent) {
        return new GlassDrawable(dp(24), dp(1), tintColor,
            prominent ? 0.22f : 0.12f, fillColor);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
