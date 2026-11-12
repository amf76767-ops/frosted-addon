package com.manu.frosted.update;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.modules.util.FrostedUpdater;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Drives the update flow: check in the background, ask on the title screen, download, verify,
 * then ask whether to restart. Network work runs on a helper thread; all screens are opened from
 * the client tick, so nothing touches the render thread from the outside.
 *
 * Nothing is installed without a yes from the player, and the old jar is kept as .bak.
 */
public final class UpdateManager {
    public static final UpdateManager INSTANCE = new UpdateManager();

    private enum State { IDLE, CHECKING, AVAILABLE, PROMPTED, DOWNLOADING, READY, DONE }

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private volatile State state = State.IDLE;
    private volatile ReleaseInfo release;
    private volatile UpdateInstaller.Plan plan;
    private volatile String notice;
    private volatile boolean noticeIsError;
    private volatile boolean manual;
    private volatile boolean checkRequested;

    private boolean started;
    private boolean announcedInWorld;
    private String currentVersion = "";
    private Path currentJar;
    private String mcVersion;

    private UpdateManager() {}

    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(INSTANCE);
    }

    /** Called by the frosted-updater module when the player clicks it. */
    public void requestCheck() {
        checkRequested = true;
    }

    // ---------------------------------------------------------------- tick

    @EventHandler
    private void onTick(TickEvent.Post event) {
        FrostedUpdater cfg = Modules.get().get(FrostedUpdater.class);
        if (cfg == null) return;

        if (!started) {
            started = true;
            resolveCurrent();
            applyPending();
            if (cfg.autoCheck()) startCheck(cfg, false);
        }

        if (checkRequested) {
            checkRequested = false;
            if (state == State.IDLE || state == State.DONE) {
                state = State.IDLE;
                startCheck(cfg, true);
            } else {
                say("Already working on an update, give it a moment.", false);
            }
        }

        String n = notice;
        if (n != null) {
            notice = null;
            if (mc.world != null) {
                if (noticeIsError) cfg.warning("%s", n);
                else cfg.info("%s", n);
            } else {
                showDialog(n);
            }
        }

        switch (state) {
            case AVAILABLE -> {
                if (manual || mc.currentScreen instanceof TitleScreen) {
                    promptUpdate();
                } else if (mc.world != null && !announcedInWorld && release != null) {
                    announcedInWorld = true;
                    cfg.info("Frosted %s is available. You will be asked on the title screen, or click the frosted-updater module to update now.", release.version());
                }
            }
            case READY -> handleReady(cfg);
            default -> { }
        }
    }

    // --------------------------------------------------------------- steps

    private void startCheck(FrostedUpdater cfg, boolean manualCheck) {
        if (currentJar == null) {
            if (manualCheck) say("Updates only work for the installed jar, not in a development environment.", true);
            return;
        }

        String repo = cfg.repo();
        if (!UpdateClient.validRepo(repo)) {
            if (manualCheck) say("Set 'repo' in the frosted-updater settings first (format: owner/name).", true);
            return;
        }

        state = State.CHECKING;
        manual = manualCheck;
        boolean prereleases = cfg.prereleases();

        async(() -> {
            try {
                ReleaseInfo latest = UpdateClient.fetchLatest(repo, prereleases);
                if (latest == null) {
                    if (manualCheck) say("No release found in " + repo + ".", false);
                    state = State.IDLE;
                } else if (Version.compare(latest.version(), currentVersion) > 0) {
                    release = latest;
                    state = State.AVAILABLE;
                } else {
                    if (manualCheck) say("Frosted is up to date (" + currentVersion + ").", false);
                    state = State.IDLE;
                }
            } catch (Exception e) {
                FrostedAddon.LOG.warn("Update check failed: {}", e.getMessage());
                if (manualCheck) say("Update check failed: " + e.getMessage(), true);
                state = State.IDLE;
            }
        });
    }

    private void promptUpdate() {
        ReleaseInfo r = release;
        if (r == null) {
            state = State.IDLE;
            return;
        }

        state = State.PROMPTED;
        manual = false;
        Screen parent = mc.currentScreen;

        Text title = Text.literal("Frosted " + r.version() + " is available").formatted(Formatting.AQUA);
        Text body = Text.literal("You have " + currentVersion + ".\n\n" + shorten(r.notes(), 6, 260)
            + "\n\nDownload and install it now?");

        mc.setScreen(new ConfirmScreen(yes -> {
            mc.setScreen(parent);
            if (yes) startDownload();
            else state = State.DONE;
        }, title, body, Text.literal("Update now"), Text.literal("Not now")));
    }

    private void startDownload() {
        ReleaseInfo r = release;
        Path jar = currentJar;
        if (r == null || jar == null) {
            state = State.DONE;
            return;
        }

        state = State.DOWNLOADING;
        Path dir = jar.getParent();

        async(() -> {
            try {
                Path file = UpdateClient.download(r, dir);
                try {
                    UpdateClient.verifyJar(file, r.version(), mcVersion);
                } catch (IOException e) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ignored) {
                        // nothing more to do
                    }
                    throw e;
                }

                if (r.sha256() == null) {
                    FrostedAddon.LOG.warn("The release has no checksum; only the jar contents were verified.");
                }
                plan = UpdateInstaller.plan(jar, file, r.version());
                state = State.READY;
            } catch (Exception e) {
                FrostedAddon.LOG.warn("Update failed", e);
                say("Update failed: " + e.getMessage(), true);
                state = State.DONE;
            }
        });
    }

    private void handleReady(FrostedUpdater cfg) {
        state = State.DONE;
        UpdateInstaller.Plan p = plan;
        if (p == null) return;

        // In a world: do not interrupt, just apply when the game closes.
        if (mc.world != null) {
            if (schedule(p, false)) cfg.info("Frosted %s is installed and becomes active after you restart the game.", p.version());
            return;
        }

        Screen parent = mc.currentScreen;
        String manualNote = UpdateInstaller.isWindows() ? "\n\n(On Windows you start the game again yourself.)" : "";

        mc.setScreen(new ConfirmScreen(restart -> {
            mc.setScreen(parent);
            if (restart) {
                if (schedule(p, true)) mc.scheduleStop();
            } else {
                schedule(p, false);
            }
        }, Text.literal("Frosted " + p.version() + " is installed").formatted(Formatting.AQUA),
            Text.literal("Restart Minecraft now to use it?\n\nWith Later, the new version is applied when you close the game." + manualNote),
            Text.literal("Restart now"), Text.literal("Later")));
    }

    /** Leftover download from an earlier session: apply it when the game closes. */
    private void applyPending() {
        if (currentJar == null) return;
        Path dir = currentJar.getParent();

        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "frosted-*.jar.update")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                String version = name.substring("frosted-".length(), name.length() - ".jar.update".length());

                if (Version.compare(version, currentVersion) <= 0) {
                    Files.deleteIfExists(file);
                    continue;
                }

                try {
                    UpdateClient.verifyJar(file, version, mcVersion);
                    UpdateInstaller.Plan p = UpdateInstaller.plan(currentJar, file, version);
                    plan = p;
                    if (schedule(p, false)) FrostedAddon.LOG.info("Frosted {} is waiting and will be applied when the game closes.", version);
                } catch (IOException e) {
                    FrostedAddon.LOG.warn("Discarding bad pending update {}: {}", name, e.getMessage());
                    Files.deleteIfExists(file);
                }
                return;
            }
        } catch (IOException e) {
            FrostedAddon.LOG.warn("Could not look for pending updates: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------- helpers

    private boolean schedule(UpdateInstaller.Plan p, boolean relaunch) {
        try {
            List<String> command = relaunch ? UpdateInstaller.currentLaunchCommand() : List.of();
            if (relaunch && command.isEmpty() && !UpdateInstaller.isWindows()) {
                say("Could not read how the game was started, so it will not restart by itself.", false);
            }
            UpdateInstaller.schedule(p, command);
            return true;
        } catch (IOException e) {
            FrostedAddon.LOG.warn("Could not schedule the update", e);
            say("Could not schedule the update: " + e.getMessage(), true);
            return false;
        }
    }

    private void resolveCurrent() {
        FabricLoader loader = FabricLoader.getInstance();

        Optional<ModContainer> self = loader.getModContainer("frosted");
        if (self.isPresent()) {
            ModContainer container = self.get();
            currentVersion = container.getMetadata().getVersion().getFriendlyString();

            ModOrigin origin = container.getOrigin();
            if (origin.getKind() == ModOrigin.Kind.PATH) {
                List<Path> paths = origin.getPaths();
                if (paths.size() == 1 && Files.isRegularFile(paths.get(0)) && paths.get(0).toString().endsWith(".jar")) {
                    currentJar = paths.get(0);
                }
            }
        }

        Optional<ModContainer> minecraft = loader.getModContainer("minecraft");
        if (minecraft.isPresent()) mcVersion = minecraft.get().getMetadata().getVersion().getFriendlyString();
    }

    private void showDialog(String text) {
        Screen parent = mc.currentScreen;
        mc.setScreen(new ConfirmScreen(ok -> mc.setScreen(parent),
            Text.literal("Frosted updater").formatted(Formatting.AQUA), Text.literal(text),
            Text.literal("OK"), Text.literal("Close")));
    }

    private void say(String text, boolean error) {
        noticeIsError = error;
        notice = text;
    }

    private static void async(Runnable task) {
        Thread thread = new Thread(task, "frosted-updater");
        thread.setDaemon(true);
        thread.start();
    }

    private static String shorten(String notes, int maxLines, int maxChars) {
        if (notes == null || notes.isBlank()) return "(no release notes)";

        String[] lines = notes.replace("\r", "").split("\n");
        StringBuilder out = new StringBuilder();
        int used = 0;
        for (String line : lines) {
            if (line.isBlank()) continue;
            if (used >= maxLines || out.length() + line.length() > maxChars) {
                out.append("…");
                break;
            }
            if (out.length() > 0) out.append('\n');
            out.append(line.trim());
            used++;
        }
        return out.toString();
    }
}
